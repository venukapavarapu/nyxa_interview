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

The wheel (`PrizeWheel.kt`) uses a single Compose `Animatable<Float>` driving a `rotate()` around
a `Canvas`, animated with `tween(4200ms, CubicBezierEasing(0.12, 0.85, 0.2, 1))` — an ease-out
curve that front-loads speed and decelerates hard at the end, plus 6 extra full rotations before
landing, to read as "spinning then settling" rather than a mechanical snap. The target angle is
computed once (`segmentCenter` from the already-known `segmentIndex`), so the animation is a pure
function of a value that never changes mid-flight — no re-triggering, no drift.

What I measured: Compose's `Animatable` runs on the frame clock via `withFrameNanos`, so it is
inherently synced to the display's refresh rate (60fps on the target emulator/device) rather than
a fixed-step timer — there's no manual frame-pacing code to get wrong. I did not wire up
GPU-profiling tooling in the 6-hour window; what I'd check next is `adb shell dumpsys gfxinfo` /
Perfetto during the spin to confirm no dropped frames during the deceleration phase, since that's
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
of the wheel and the 1,200-item grid on a physical low-end device, not just the emulator; (3)
proper error-state screens (as opposed to inline text) for the store grid's initial-load failure
and a retry affordance, since right now a first-load failure is a dead end until app restart.

## 8. AI usage

This entire submission was built with Claude (Claude Code) as the primary implementation tool,
end to end: architecture layout, the mock backend, all MVI features, the native wheel/reveal
animations, and this test suite were AI-authored from the assignment PDF and my instructions to
follow SOLID/Clean Architecture/MVI/Hilt/coroutines-Flow/TDD, with me reviewing and directing at
each step (e.g., choosing Mystery Box Reveal over VIP Membership as the optional feature, and
choosing an in-process Kotlin fake over a local HTTP server for the mock backend).

**A case where the AI output was incorrect and was overridden:** the first draft of
`CartLine`/`Product` put a `priceCents` extension property on `ProductVariant` that returned a
hardcoded `0L` as a placeholder — a bug that would have made every cart line price to zero. This
was caught during the build and moved to `Product.priceCents` (the variant doesn't carry its own
price in this domain model; the product does), fixed as `CartLine.lineTotalCents = product.priceCents * quantity`.
A second case: the initial Gradle version catalog guessed several library versions
(Room `2.9.5`, KSP `2.2.10-2.0.4`, Hilt `2.58.1`, `core-ktx`/`lifecycle` versions that turned out
to require `compileSdk 37`, which isn't installed locally) that didn't actually exist or weren't
compatible with the installed SDK — these were caught by running the real Gradle build against
Google's Maven metadata and corrected to versions that resolve and that satisfy AAR metadata
checks against the locally available `compileSdk 36.1`, rather than trusting the first guess.
