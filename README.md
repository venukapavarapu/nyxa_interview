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
response." Two ways to see it:

**A. On demand (recommended for the recording):** On the Spin tab, tap **"Debug: drop next spin's
response"** (visible in debug builds only, via `BuildConfig.DEBUG`) before tapping **Spin**. This
forces exactly the next spin request to simulate a dropped connection after the server has
already settled it. The Spin button stays disabled and the header shows **"Resolving…"** while
the app polls the recovery endpoint in the background; within a couple of seconds it recovers the
real result and the wheel animates to it — the same result you were charged for, never a re-roll.

**B. Naturally:** the mock's baseline 15% random failure rate will eventually produce the same
scenario on an ordinary spin without the debug toggle — the recovery path is identical either way,
the debug toggle just makes it deterministic for the demo instead of waiting on chance.

If the app is killed mid-recovery (e.g. via "Force stop" from Android system settings) and
relaunched, the pending spin is picked up again on cold start from Room-backed local state — no
network needed to know a spin is still unresolved.

## Tests

`./gradlew :app:testDebugUnitTest` — unit tests covering (per section 5 of the brief):

1. **Spin result recovery path** — `domain/usecase/SpinRecoveryTest.kt`: dropped-connection
   charge-once behavior, recovery clearing pending state, transient-failure-vs-still-unknown
   handling.
2. **Checkout idempotency** — `domain/usecase/CheckoutUseCaseTest.kt` (use-case level, mocked
   repositories) and `data/remote/mock/MockBackendCheckoutIdempotencyTest.kt` (server-level
   de-dup, including a simulated concurrent double-tap).
3. **The Product normaliser** — `data/remote/mapper/ProductNormalizerTest.kt`: both wire shapes,
   malformed-price fallback, sold-out variant detection.

Plus `data/remote/mock/MockBackendSpinIdempotencyTest.kt` (server-side spin de-dup and credit
accounting) and `presentation/navigation/ProductRouteEncodingTest.kt` (regression test for a real
bug found during manual testing — see below).

## A bug found via manual on-device testing

While walking the store flow on an emulator, tapping a product crashed the app:
`IllegalArgumentException: Navigation destination that matches route product/gid://shopify/Product/0
cannot be found`. Product ids contain `/` (e.g. `gid://shopify/Product/0`), and
Navigation-Compose's default String path-argument parsing splits on `/`, so the id broke the route
pattern. Fixed by URL-encoding the id when building the route and decoding it when reading the nav
argument (`presentation/navigation/NyxaNavHost.kt`, `presentation/store/detail/ProductDetailViewModel.kt`),
with a regression test added. Left in this README because it's a good illustration of why the
assignment's "screen recording + live review" format matters — this class of bug is invisible from
reading the code and only shows up when you actually tap through the app.

## Screen recording

See the submission's attached recording, or reproduce it: login → Spin tab, tap the debug drop
toggle then Spin (shows recovery) → Store tab → tap a product → Add to cart → Cart → Place order
(retry once if the 15% failure hits) → Wallet tab showing the updated ledger and balance.
