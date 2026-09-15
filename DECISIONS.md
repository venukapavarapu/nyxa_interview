# Decision Log

## 1. Architecture

The app is Clean Architecture in three layers, each a package under `com.example.nyxa_interview`:

- **`domain/`** — pure Kotlin, zero Android/Compose/Room imports. Models (`Product`, `Wallet`,
  `SpinResult`...), repository *interfaces* (ports), and use cases that hold the business rules
  (`PerformSpinUseCase`, `CheckoutUseCase`, `RecoverPendingGamesUseCase`). This layer is what the
  live review can reason about without touching a single Compose file.
- **`data/`** — implements the domain interfaces. `MockBackend` stands in for the real Shopify +
  games API described in the brief; `ProductNormalizer` is the anti-corruption layer that resolves
  the two catalogue wire shapes into one domain `Product`; Room (`AppDatabase`) is the local cache
  for wallet/ledger and pending-game-action durability; `EncryptedTokenStore` wraps Android Keystore.
- **`presentation/`** — one package per feature (`auth/login`, `store/grid`, `store/detail`,
  `checkout`, `spin`, `box`, `wallet`), each following MVI: `*Contract.kt` (State/Intent/Effect),
  `*ViewModel.kt` (extends a shared `MviViewModel` base), and a stateless `*Screen.kt` Composable
  that only reads `State` and emits `Intent`s.

SOLID mapping: repository interfaces + `@Binds` in Hilt modules give Dependency Inversion; each
use case has one reason to change (Single Responsibility); `AppResult`/`AppError` are closed
for modification but new error variants are additive; ViewModels depend on interfaces, never on
`MockBackend` or Room directly (Interface Segregation via narrow repository contracts).

**At 40 screens and six games** I would:
- Split `presentation/` into separate Gradle feature modules (`:feature:spin`, `:feature:store`...)
  so unrelated features can't accidentally couple through shared ViewModels, and so CI only
  rebuilds/tests what changed.
- Promote `domain/` and `data/` to their own modules (`:core:domain`, `:core:data`) consumed by
  every feature module, enforcing the dependency direction at the build-graph level, not just by
  convention.
- Introduce a `:core:navigation` module with route contracts (sealed route classes) so feature
  modules don't depend on each other's route strings directly.
- Extract the MVI base classes and `AppResult`/`DispatcherProvider` into a `:core:ui` /
  `:core:common` module.

## 2. Game integrity

Outcome authority lives entirely on the server (`MockBackend.spin()` / `.openBox()`). The client
never computes a segment index or a prize — it requests an action, gets back an already-decided
`SpinResult`/`BoxResult`, and the wheel/reveal animation is driven *to* that result
(`PrizeWheel`'s `targetSegmentIndex` parameter, `BoxRevealAnimation`'s `revealSequence`). See
`PerformSpinUseCase` — it calls `gamesRepository.spin()` and only then reads `segmentIndex` off
the response; there is no code path where the client picks a segment first.

**What a dishonest user would attempt, and what stops it:**
- *Replaying a win result client-side* (intercept/replay a favorable `SpinResult` JSON): stopped
  because the wheel only animates to results this session actually requested and the wallet
  balance is server-confirmed on `refresh()`, not derived from whatever the wheel displayed.
- *Client-side outcome tampering* (patch the segment index before animating): irrelevant, because
  the wallet ledger — the thing that actually represents money — is written server-side in
  `MockBackend.settleSpin()`/`openBox()`, not from whatever the client displays. A modified client
  could show a fake wheel result, but it could never make the server credit a fake prize.
- *Double-spinning via double-tap*: stopped by `SpinUiState.isSpinEnabled` disabling the button for
  the *entire* duration of the phase machine (REQUESTING → ANIMATING/RESOLVING → SETTLED), and by
  the idempotency key ensuring even a race that slips past the UI guard still de-dupes server-side.
- *Retrying a spin to reroll a bad result*: stopped by the idempotency key — `MockBackend.spin()`
  returns the **same** `SpinResult` for a replayed key (see `MockBackendSpinIdempotencyTest`), so
  retrying never re-rolls.

`serverSeed` and `clientSeedEcho` are the hooks for **provable fairness**: in production, the
server would commit to `serverSeed` (or its hash) before the spin, and `clientSeedEcho` lets the
client contribute entropy it can later use to independently verify the outcome was not
after-the-fact adjusted for that specific user (a standard provably-fair pattern from gambling/
sweepstakes platforms). Today the UI doesn't surface a "verify this spin" screen, but the fields
already flow through `SpinResult` so that screen is additive, not a re-architecture.

## 3. Charge-once guarantees (dropped-connection spin walkthrough)

Sequence, matching `PerformSpinUseCase`:

1. Client generates `idempotencyKey`, **persists it locally as pending** (Room, via
   `PendingGameActionRepository.markPending`) *before* any network call. **Failure point A:**
   process death right here — nothing was charged yet, nothing to recover, safe.
2. Client calls `POST /games/spin` with that key.
3. **Failure point B (the brief's named scenario):** server receives the request, settles the
   spin (decrements a spin credit, appends a ledger entry, stores the result keyed by
   `idempotencyKey` and by `resultId`), but the response never reaches the client — `MockBackend`
   simulates this exactly: `settleSpin()` runs unconditionally, then `consumeForcedSpinDrop()` /
   the random failure check throws *after* settlement. The client sees a network/timeout error.
4. Client's `PerformSpinUseCase` returns `SpinOutcome.Unresolved` — critically, it does **not**
   clear the pending record from step 1. `SpinViewModel.resolvePending()` then polls
   `GET /games/spin/{resultId}`-equivalent (`findSpinByIdempotencyKey`) with backoff.
5. Once the server has the result (it always does, by step 3's construction), recovery finds it,
   the pending record is cleared, the wallet is refreshed, and the wheel animates to the real
   result the user already paid for.
6. **Failure point C:** the app is killed before recovery finishes. On next launch,
   `SpinViewModel.recoverOnLaunch()` re-reads pending records from Room (durable, survives process
   death) and resumes the same recovery flow.

Net effect: the spin credit is decremented exactly once (server-side, at settlement — never on the
client), and the user is guaranteed to eventually see the result they paid for, regardless of how
many times the request is retried or the app is killed, because every retry reuses the same
`idempotencyKey` and the server's `spinResultsByKey` map is authoritative.

## 4. Animation

The wheel (`PrizeWheel.kt`) is a single Compose `Animatable<Float>` driving a `rotate()` around a
`Canvas`, in two phases that share the same value with no snap between them:

1. **Waiting on the server** — the instant a spin is requested, `rotation` is advanced directly
   off the frame clock (`withFrameNanos`, not `tween()`) at a fixed rate of one lap per 700ms.
   Driving it off raw frame deltas rather than chained fixed-duration tweens matters: an earlier
   version chained a `tween()` per 360° lap and re-synced (visibly changing speed) at every lap
   boundary — a bug only caught by watching the actual animation, not by reading the code.
2. **Landing** — the moment the server result arrives, the wheel decelerates onto the exact
   center of the target segment. Rather than a fixed duration, the tween's *duration* is solved
   per spin (`durationMs = 2 * distance / FREE_SPIN_DEGREES_PER_MS`) so that its peak
   instantaneous speed — which for the quadratic ease-out used here lands at t=0, the exact
   moment the response arrives — always equals the waiting phase's own speed. It can only
   decelerate from there, never speed up, regardless of how far away the target segment happens
   to be that spin. An earlier version fixed the duration instead and only capped the *distance*,
   which meant a far-away segment forced a faster-than-free-spin starting speed — visible as the
   wheel "speeding up" right as landing began. Caught and fixed the same way, by watching it.

What I measured: Compose's `Animatable` and `withFrameNanos` both run on the compositor's own
frame clock, so both phases are inherently synced to the display's refresh rate (60fps on the
target emulator/device) rather than a fixed-step timer — there's no manual frame-pacing code to
get wrong. I did not wire up GPU-profiling tooling in the 6-hour window; what I'd check next is
`adb shell dumpsys gfxinfo` / Perfetto during the spin to confirm no dropped frames, since that's
where jank would be most visible (many `drawText` calls per frame at full segment count).

**Would this scale to Plinko?** No, not as-is. The wheel's animation is a single deterministic
closed-form path (rotation as a function of time) because the *only* piece of information from the
server is "which segment," and any rotation ending there is a valid visual. Plinko is different:
the ball's path itself carries meaning (it must look like it's obeying gravity and bouncing off
real pegs) and the *server* would need to define more than an end slot — either a full path
(list of peg-collision events/positions, similar in spirit to `revealSequence` but denser) for the
client to replay, or physics parameters (initial x-offset, restitution) deterministic enough that
client-side physics simulation reproduces the server-declared outcome exactly. I'd move to a
`Canvas`-driven physics step loop (verlet or a simple gravity+collision integrator) consuming a
server-provided path/seed rather than a single `Animatable`, structurally closer to
`BoxRevealAnimation`'s checkpoint-sequence approach than to `PrizeWheel`'s single-tween approach.

## 5. App Store and Play Store policy

- **Physical merchandise (shirts, decals, car care products):** can use the store's own payment
  flow (Shopify/Stripe-style checkout) on both platforms — this is a real-world good shipped to
  the customer, squarely in Apple's and Google's "physical goods" exemption from IAP.
- **Sweepstakes entries purchased directly, and VIP membership:** this is the riskiest bucket.
  Entries are not a physical good and not consumable digital content in the traditional sense —
  they're closer to a sweepstakes/lottery mechanic, which both platforms restrict heavily
  (Apple guideline 3.1.1 carve-outs are narrow; Google Play's Gambling and Contests policy applies
  to sweepstakes with a prize of monetary value). VIP membership-as-subscription, if it functions
  as "unlocks in-app content/features," likely **must** use StoreKit 2 / Play Billing (Option B in
  this assignment). If VIP membership is purely "ships you extra physical perks," it may qualify
  for the physical-goods exemption instead — this hinges on what the membership actually grants.
- **Instant-win games with cash prizes (Spin to Win, Mystery Boxes):** the highest-risk bucket.
  A game with a *cash* prize, purchased with real money, is functionally gambling/a sweepstakes in
  most jurisdictions and under both stores' policies, regardless of payment rail. This may not be
  permitted at all without a specific sweepstakes/gambling review and jurisdiction-by-jurisdiction
  legal clearance (many US states regulate "pay to play" prize games separately from "no purchase
  necessary" sweepstakes — the classic sweepstakes structure avoids this by offering a free
  alternate method of entry). This is very plausibly the reason Redline's *current* app is a
  Tapcart (web) wrapper rather than native — native apps face app-store review on exactly this
  point in a way a mobile web checkout does not.
- **What I'd verify with legal + platform before shipping:** (1) whether "no purchase necessary"
  alternate entry methods are implemented and sufficiently prominent (US sweepstakes law
  essentially requires this to avoid being an illegal lottery); (2) state-by-state restrictions —
  several US states prohibit or heavily restrict pay-to-play sweepstakes entirely; (3) direct
  pre-submission conversation with both Apple and Google review teams, since "instant win game
  sold via IAP" is exactly the kind of thing that gets rejected in review even if technically
  compliant, and both platforms have precedent for case-by-case judgment calls here; (4) whether
  entries/spins/boxes should legally be *earned* (free with purchase of something else) rather
  than *sold directly* — that would remove them from IAP-gambling territory into ordinary
  promotional-mechanic territory.
- **Structuring the code so a policy rejection doesn't require a rewrite:** the domain layer
  already treats "how you pay" as external to "what you get" — `CheckoutRepository`,
  `GamesRepository`, and use cases only deal in domain concepts (entries, prizes, orders), never
  in payment-rail specifics. If Apple/Google force entries-purchased-directly or VIP membership
  behind IAP, that's a `data/` layer swap (a new `CheckoutRepositoryImpl`/`GamesRepositoryImpl`
  backed by StoreKit2/Play Billing instead of the Shopify-style checkout) with zero change to
  `domain/` or `presentation/`. This is the concrete payoff of the repository-interface boundary
  described in section 1.

## 6. Shopify

Swapping the mock for the real Shopify Storefront API would primarily change `data/remote/`:
`MockBackend` is replaced by a real GraphQL client (Apollo/Ktor) hitting Storefront API for
products/collections and Shopify's Cart API for cart mutations, while `ProductNormalizer` gets a
single real input shape to map instead of two mock shapes (the "two shapes" quirk in this
assignment simulates the kind of legacy/migrated-data inconsistency a real backend can have, but
Storefront API itself is internally consistent). Checkout would move from a custom `/checkout`
endpoint to either Shopify's hosted checkout (webview handoff — simplest, but loses "native"
checkout) or the Cart API's checkout URL flow. Games (spin/box) stay entirely custom since Shopify
has no concept of instant-win games — they'd likely live behind a separate first-party backend
that also *writes* entries into a Shopify metafield or a separate ledger service.

**Cart state** should live server-side (Shopify's Cart object, addressed by cart ID) rather than
purely on-device, because it must survive app restarts/device switches and because Shopify's cart
is the source of truth for pricing/discounts/availability at checkout time — the app should hold
only a thin client-side reference (the cart ID + an optimistic local mirror for instant UI
feedback), which is exactly the shape `CartRepository.cart: StateFlow<Cart?>` already has today
even against the mock.

## 7. Scope

**Deliberately left out** (beyond the explicit 4.6 out-of-scope list): server-driven wheel segment
theming/config screen; a "verify this spin was fair" seed-reveal screen (the data is there —
`serverSeed`/`clientSeedEcho` — but no UI consumes it); retry/backoff configuration exposed to the
user; multi-collection store browsing (only one collection, per 4.4's "single collection"
requirement); accessibility pass (TalkBack labels, dynamic type) beyond default Material3
behavior; offline queuing of checkout/spin *requests* themselves (today, "offline" means "the
wallet/ledger still renders from cache," not "you can spin with no network and it queues").

**First three hours if I had them:** (1) a seed-reveal / fairness-verification screen using the
already-plumbed `serverSeed`/`clientSeedEcho`, since it's the most direct way to make "game
integrity" tangible rather than just asserted in this log; (2) real GPU-profiled 60fps validation
of the wheel and the 1,200-item grid on a physical low-end device, not just the emulator; (3) an
optimistic-update path for cart quantity changes (increase/decrease on the cart page currently
waits on the mock round-trip before updating, which is correct but not as snappy as it could be).

(Store grid initial-load failure now does show a proper error state with a Retry button — found
missing during manual on-device testing and fixed; noted here since it was originally listed as
a gap in this section.)

## 8. AI usage

I used Claude (Claude Code) as a secondary source and to implement things faster on my commands —
not as something I fully depended on. I directed the architecture and decisions (Clean
Architecture layering, MVI, choosing Mystery Box Reveal over VIP Membership, choosing an
in-process Kotlin fake over a local HTTP server for the mock backend, the charge-once/idempotency
design for spins and checkout) and used the tool to generate the resulting Kotlin/Compose code
against those decisions, then reviewed, ran, and corrected it myself rather than taking output at
face value. A concrete example of that review loop: after the spin wheel and mystery box features
were in place, I manually exercised the app on an emulator and reported specific defects back
one at a time — the wheel not responding to taps, its animation accelerating partway through
instead of holding a constant speed, spin credits still being consumed after they hit zero, an
"Add to cart" button whose text had become invisible after a layout change, and a store screen
that went silently blank on a failed product load — each of which required tracing to a real root
cause (a stale `LaunchedEffect` key, a mismatched tween velocity at a phase handoff, a missing
credit check before settlement, a missing `verticalScroll` after adding new content, a missing
error/retry branch) rather than a surface-level fix, and I verified each fix by rebuilding and
retesting on-device before accepting it.

**Cases where the AI output was incorrect and was overridden:**
- The first draft of `CartLine`/`Product` put a `priceCents` extension property on
  `ProductVariant` that returned a hardcoded `0L` as a placeholder — a bug that would have made
  every cart line price to zero. Moved to `Product.priceCents` (the variant doesn't carry its own
  price in this domain model; the product does): `CartLine.lineTotalCents = product.priceCents * quantity`.
- The initial Gradle version catalog guessed several library versions (Room `2.9.5`, KSP
  `2.2.10-2.0.4`, Hilt `2.58.1`, `core-ktx`/`lifecycle` versions requiring `compileSdk 37`, which
  isn't installed locally) that didn't exist or weren't compatible with the installed SDK —
  corrected against Google's Maven metadata and the locally available `compileSdk 36.1`.
- The wheel animation went through several incorrect iterations before landing on a correct one:
  an early version chained per-lap `tween()` calls for the "waiting on the server" spin, which
  re-synced (and briefly changed speed) at every lap boundary; a later version matched the
  landing animation's *starting* velocity to the wrong reference, so it visibly sped up at the
  exact moment the server response arrived instead of only ever decelerating. Both were caught by
  watching the actual animation on-device, not by reasoning about the code alone, and fixed by
  driving the waiting phase off the raw frame clock and solving the landing tween's duration per
  spin so its peak speed is capped at the waiting phase's speed rather than fixed to a constant
  duration regardless of distance.
