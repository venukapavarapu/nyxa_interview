# Redline Society — Native Vertical Slice

**Platform:** Native Android (Kotlin, Jetpack Compose). Chosen for direct control over 60fps
custom Canvas animation (the prize wheel and mystery box reveal) and Android Keystore-backed
secure token storage, both called out explicitly in the assignment brief.

## Architecture at a glance

- **Clean Architecture**, 3 layers: `domain/` (pure Kotlin — models, repository interfaces, use
  cases), `data/` (repository implementations, the in-process mock backend, Room cache, encrypted
  token store), `presentation/` (one MVI feature per screen: Contract + ViewModel + Screen).
- **MVI**: every feature has an immutable `UiState`, a sealed `UiIntent`, and a sealed one-shot
  `UiEffect`, driven by a shared `MviViewModel` base class.
- **DI**: Dagger Hilt throughout.
- **Async**: Kotlin Coroutines + Flow everywhere; no callbacks, no RxJava.
- **Persistence**: Room (wallet/ledger cache + pending-game-action durability), Android Keystore
  via `androidx.security.crypto.EncryptedSharedPreferences` (auth tokens).
- **Optional feature implemented**: Option A, Mystery Box Reveal.

See `DECISIONS.md` for the full architecture/design rationale, game-integrity walkthrough, and
the platform-policy analysis.

## Running the app (< 5 minutes)

Requirements: Android Studio (Narwhal/Otter or newer, bundled with AGP 9.x support), JDK 17+, an
Android emulator or device on API 24+.

1. Open the project root in Android Studio and let Gradle sync (or from the terminal:
   `./gradlew :app:assembleDebug`).
2. Run the `app` configuration on an emulator/device (or `./gradlew installDebug` + launch
   manually).
3. **Login**: any non-empty email and password work — the mock backend accepts any credentials
   (registration/password validation is explicitly out of scope per section 4.6). If you see
   "Network hiccup, please try again," that's the mock's built-in 15% random failure rate — tap
   Sign in again.
4. You'll land on the Store tab. Bottom navigation: **Store → Spin → Boxes → Wallet**.

No backend to stand up separately — the mock API (section 3 of the brief) is implemented
in-process as `data/remote/mock/MockBackend.kt`, so the app is fully runnable offline/without a
server. It simulates 400–1500ms latency and a 15% random failure rate on every call, matching the
spec.

## Triggering the dropped-connection spin in the mock

The brief requires demonstrating "the server settles a spin but the client never receives the
response." The mock's baseline 15% random failure rate produces this scenario naturally on an
ordinary spin — no separate toggle needed. When it happens: the wheel keeps spinning (it never
freezes or shows a "Resolving…" status message — the animation itself is the only feedback while
recovery runs in the background) and the Spin control stays disabled until recovery completes;
within a couple of seconds it finds the real result and the wheel decelerates onto it — the same
result you were charged for, never a re-roll. Since it's a 15% chance per spin, retapping Spin a
few times will reliably reproduce it for the recording.

If the app is killed mid-recovery (e.g. via "Force stop" from Android system settings) and
relaunched, the pending spin is picked up again on cold start from Room-backed local state — no
network needed to know a spin is still unresolved.

## Tests

`./gradlew :app:testDebugUnitTest` — 40+ unit tests covering (per section 5 of the brief) and
beyond:

1. **Spin result recovery path** — `domain/usecase/SpinRecoveryTest.kt`: dropped-connection
   charge-once behavior, recovery clearing pending state, transient-failure-vs-still-unknown
   handling, and a spin rejected for insufficient credits never entering recovery (nothing was
   charged, so there's nothing to recover).
2. **Checkout idempotency** — `domain/usecase/CheckoutUseCaseTest.kt` (use-case level, mocked
   repositories) and `data/remote/mock/MockBackendCheckoutIdempotencyTest.kt` (server-level
   de-dup, including a simulated concurrent double-tap).
3. **The Product normaliser** — `data/remote/mapper/ProductNormalizerTest.kt`: both wire shapes,
   malformed-price fallback, sold-out variant detection.

Plus `data/remote/mock/MockBackendSpinIdempotencyTest.kt` (server-side spin de-dup and credit
accounting), `data/remote/mock/MockBackendSpinCreditsTest.kt` and `presentation/spin/SpinUiStateTest.kt`
(a spin must be rejected once credits hit zero — see the bug list below),
`data/remote/mock/MockBackendCartQuantityTest.kt` and `presentation/checkout/CheckoutViewModelQuantityTest.kt`
(cart quantity increase/decrease, merge-on-same-variant, remove-on-zero), `domain/model/CartTest.kt`,
and `presentation/navigation/ProductRouteEncodingTest.kt` (regression test for a bug found during
manual testing — see below).

## Bugs found via manual on-device testing

This app was built with an AI pair-programming tool (see `DECISIONS.md` section 8) but every
feature was exercised by hand on an emulator before being called done, and several real defects
only surfaced that way — none of them were visible from reading the code alone:

- **Navigation crash on any product tap.** Product ids contain `/` (e.g.
  `gid://shopify/Product/0`), and Navigation-Compose's default String path-argument parsing splits
  on `/`, so the id broke the route pattern with `IllegalArgumentException: Navigation destination
  ... cannot be found`. Fixed by URL-encoding the id when building the route and decoding it when
  reading the nav argument.
- **Spin credits could go negative-effectively-free.** With 0 spin credits, tapping Spin still
  fired a request and the mock server settled it anyway, silently clamping the credit counter at
  zero instead of rejecting the spin — a real financial-integrity bug, letting a user spin (and
  win) for free indefinitely. Fixed by rejecting the request server-side before settlement.
- **Wheel animation speed bugs**, found only by watching it run: chained per-lap tween calls
  caused a visible speed change at every full rotation; a later fix still let the landing
  animation start faster than the "waiting" spin when the target segment was far away. Both fixed
  by driving the wait phase off the raw frame clock and solving the landing tween's duration per
  spin so it can only ever decelerate, never speed up.
- **"Add to cart" button text nearly invisible.** Adding the quantity stepper to the product
  detail page pushed the button below the screen's visible area (no scroll on that screen), so
  only a sliver of clipped pixels was visible. Fixed by adding `verticalScroll`.
- **Store grid went silently blank on a failed load.** The mock's normal 15% random failure rate
  hitting the initial product fetch left the screen empty with no error message and no retry
  affordance. Fixed by adding a proper error state with a Retry button.

Left in this README because it's a good illustration of why the assignment's "screen recording +
live review" format matters — every one of these is invisible from reading the code and only
showed up when the app was actually run and tapped through.

## Screen recording

See the submission's attached recording, or reproduce it: login → Spin tab, tap the debug drop
toggle then Spin (shows recovery) → Store tab → tap a product → Add to cart → Cart → Place order
(retry once if the 15% failure hits) → Wallet tab showing the updated ledger and balance.
