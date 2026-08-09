# Twofold — Architecture

## Stack

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Foldable | `androidx.window` — `WindowInfoTracker`, `FoldingFeature` |
| Hinge angle | `Sensor.TYPE_HINGE_ANGLE` (Samsung + Pixel Fold), for transition animation only |
| PDF rendering | `android.graphics.pdf.PdfRenderer` / `PdfDocument` (AOSP) — the agent's half |
| PDF text | PdfBox-Android 2.0.27 (Apache 2.0) — the client's half needs words, and the platform exposes none |
| Scanned text | ML Kit text recognition, on-device, Latin + Devanagari |
| Translation | ML Kit on-device translation — Hindi, Tamil, Bengali, Marathi |
| Storage | JSON via `org.json` + app-private files (no Room) |
| Type | Source Serif 4 + Inter, embedded (SIL OFL) |
| Billing | `purchases-android` 10.16.1 with `purchases-store-galaxy`, `GalaxyConfiguration` |
| minSdk / compileSdk | 30 / 37.1 |
| Build | AGP 9.3.1 on Gradle 9.7 — AGP 9 has built-in Kotlin, so no `kotlin.android` plugin |

**No Room.** The persisted schema is four fields of notes, a session record, and a cached
entitlement flag. Room would add a compiler plugin, a migration framework and a code-generation step
to store less data than a single screen displays. `org.json` is in the platform and costs nothing.
Revisit if the schema ever grows past what fits on one page.

Native Android, not cross-platform. The whole product is a foldable posture API and a PDF renderer;
a cross-platform layer would only get in the way of both. (Compose Multiplatform would unlock the
JetBrains category, but not at the cost of the thing we're actually judged on.)

## Posture detection

`WindowInfoTracker.windowLayoutInfo()` emits `FoldingFeature` with `state` and `bounds`. That gives
both *which posture* and *where the crease is in window coordinates* — the second is what actually
splits the layout, and it must never be hardcoded to 50%.

```
FLAT     + occlusionType NONE + orientation HORIZONTAL  →  Twofold mode
HALF_OPENED + orientation HORIZONTAL                    →  Present mode
no folding feature / folded                             →  Prepare mode
```

Two guards, both learned from how these APIs actually behave:

1. **Never assume a fold exists.** The same APK runs on a flat-screen Galaxy, in the emulator, and on
   a Fold. `Prepare` is the default and every other mode is an enhancement.
2. **Debounce the posture flow.** Physically opening a device emits a burst of intermediate states.
   Entering Twofold mode must require the posture to hold, or the UI thrashes in the demo.

## The two-pane split

One composable tree, split by the crease bounds, with the far pane counter-rotated:

```
TwofoldScaffold(foldBounds) {
  ClientPane(  modifier = Modifier.height(farHalf).rotate(180f) )
  AgentPane(   modifier = Modifier.height(nearHalf) )
}
```

The 180° rotation is what makes the far half readable to the person opposite. Which half is "far" is
a user setting with a swap control — there is no reliable sensor for which side of the table the
agent is sitting on, and guessing wrong is worse than asking once.

Touch handling stays in unrotated coordinates for the agent pane; the client pane accepts touch only
in signature mode, where the rotation must also be applied to the captured path.

## The leak guarantee

The single most important structural decision in the codebase.

The client renderer takes a type that cannot express agent content:

```kotlin
data class ClientPage(
  val clause: Clause?,
  val clauseNumber: Int,
  val clauseCount: Int,
  val legibility: Float,
  val isPreparing: Boolean,
)
// no notes field. no talk track. no way to add one without changing this type.
```

`Clause` is checked too: since `ClientPage` is now a thin wrapper around it, the guarantee is only
as strong as `Clause` is, and a note attached to a clause would sail past a check on the wrapper.

`ClientPane` is a pure function of `ClientPage`. `AgentPage` is a separate type that composes a
`ClientPage` plus the private layer. Notes flow agent-ward only, and the compiler enforces it.

A styling bug can leak a colour. It must not be able to leak a note in front of a paying customer.

**Enforced by `LeakGuaranteeTest`**, which reflects over `ClientPage`'s declared fields and fails on
anything resembling private data, asserts `AgentPage` composes a `ClientPage` rather than
duplicating it, and pins the exact field set so that adding one forces someone to come back here and
think. Verified by mutation: adding a `note` field to `ClientPage` fails two of the three tests.

## Package layout

Packages inside a single `:app` module, not separate Gradle modules. At this size the module
boundaries would buy nothing but configuration time; the package structure already carries the
architecture. Split when a second app or a shared library needs one of these.

```
com.twofold/
  MainActivity.kt         – posture → mode routing, the only place panes are composed
  core/design/            – palette, embedded type, theme            (see DESIGN.md)
  core/fold/              – FoldLogic (pure, unit-tested), FoldStateTracker, TwofoldScaffold
  data/document/          – PdfSource, PageStore cache, DocumentRepository
  data/notes/             – per-page private notes and talk track
  data/session/           – session log, signature record, signed-PDF export
  feature/present/        – ClientPane / AgentPane and the shared page state
  feature/sign/           – signature capture
  feature/sessions/       – the unsigned follow-up list
  feature/paywall/        – entitlement and the upgrade screen
```

`core/fold/FoldLogic` is deliberately free of Android types so the posture rules — the decisions the
whole product turns on — can be tested on the JVM rather than only on hardware.

## Page rendering and cache

`PdfRenderer` is single-threaded and must be serialised behind a mutex — concurrent `openPage` calls
crash. Pages render to bitmaps off the main thread, into an LRU cache sized from
`ActivityManager.memoryClass`, with the current page ±1 prefetched.

Both panes draw the *same* cached bitmap at different scales. Rendering twice is wasted work and, on
a page turn, visibly desynchronises the two halves — which breaks the illusion the entire product
rests on.

## Billing

RevenueCat with the Galaxy Store module:

```kotlin
Purchases.configure(
  GalaxyConfiguration.Builder(context, GALAXY_API_KEY).build()
)
```

One entitlement, `pro`. Everything gates on `customerInfo.entitlements["pro"]?.isActive`.

**Known constraint that shapes the schedule:** Galaxy Store IAP cannot be tested on an emulator. It
requires a physical Galaxy device signed into a Samsung account. The purchase path is therefore built
against RevenueCat's Test Store first, and validated for real only during the borrowed-device window.
See [SHIPATON.md](SHIPATON.md).

## Reading a document

Three routes, in order of preference:

1. **Embedded text layer** via PdfBox. Instant and exact.
2. **OCR** when there is none, which is common — much Indian paperwork arrives as a scan. Slow
   (~1s a page) and *approximate*: it reads in visual order, so a two-column table can flatten into
   the paragraph below it and land under the wrong heading. The agent's half says so; the client's
   does not, because a warning there would undermine a document the agent is about to explain.
3. **Nothing readable**, reported honestly rather than showing a blank half.

Text is then split by `ClauseSegmenter` — pure, Android-free, and unit-tested, for the same reason
`FoldLogic` is.

## Translation

The client's clause is translated on-device; the agent always keeps the source wording, held
separately on `AgentPage` so an agent never ends up reading a machine translation of their own
policy back to themselves.

The language is chosen on the prepare screen, not mid-meeting: a pack is tens of megabytes and a
client should never watch a progress bar. It persists across restarts, and the translator re-arms
before the first clause renders so the client never briefly sees the wrong language.

## Speech

Translation only helps a client who can read. Around a quarter of Indian adults cannot, and
functional literacy for a legal document is lower still — so the clause is also read aloud, in the
client's language, through the platform engine.

Readiness is deliberately tri-state (`SpeechReadiness`). A cold bind to the system speech service is
slow, and collapsing "hasn't answered yet" into "unavailable" put *This phone has no voice for that
language* in front of an agent whose phone had one. `UNKNOWN` keeps the control offered and settles
the question when it is pressed.

## Size

The release APK is ~39MB, and about 27MB of that is two ML Kit native libraries — translation
(16MB) and OCR (11MB). Restricting to `arm64-v8a` (every Galaxy foldable is arm64) and dropping
BouncyCastle's post-quantum test vectors already removed the worst of it.

The remaining 27MB is **kept on purpose.** ML Kit's text recognition also comes in an unbundled
variant that would save ~11MB by loading models through Google Play services on first use. That
trades size for a download before the first scanned document can be read — and "works in a living
room with no signal" is the promise the whole product rests on. An agent whose OCR needs a
connection has an app that fails in exactly the villages it was built for. Size is the cheaper
thing to spend.

## Offline

Field agents work in places with no signal. Everything except the purchase itself works fully
offline; entitlement status is cached and trusted between launches. An agent whose app locks up in a
client's living room because it couldn't reach a server does not remain a customer.

## Testing without hardware

A resizable AVD with hinge sensor support reproduces posture changes and hinge angle, and the
emulator's virtual sensor panel can drive the fold through its full range. That covers layout, mode
transitions and the leak guarantee. It does **not** cover IAP, real-world legibility, or how the
rotation reads to an actual second person — all of which need the borrowed device.
