# Twofold — Architecture

## Stack

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Foldable | `androidx.window` — `WindowInfoTracker`, `FoldingFeature` |
| Hinge angle | `Sensor.TYPE_HINGE_ANGLE` (Samsung + Pixel Fold), for transition animation only |
| PDF | `android.graphics.pdf.PdfRenderer` / `PdfDocument` (AOSP — no licence entanglement) |
| Storage | Room + app-private files |
| Billing | `purchases-android` ≥ 10.7.0 with `purchases-store-galaxy` |
| minSdk | 30 |

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
  val bitmap: Bitmap,
  val highlights: List<Rect>,
  val spotlight: Rect?,
  val legibility: Float,
)
// no notes field. no talk track. no way to add one without changing this type.
```

`ClientPane` is a pure function of `ClientPage`. `AgentPage` is a separate type that composes a
`ClientPage` plus the private layer. Notes flow agent-ward only, and the compiler enforces it.

A styling bug can leak a colour. It must not be able to leak a note in front of a paying customer.
This is also tested: a screenshot test asserts no note text appears in the client pane's rendered
output.

## Module layout

```
app/                    – application, navigation, DI wiring
core/design/            – theme, type scale, tokens  (see DESIGN.md)
core/fold/              – posture flow, TwofoldScaffold, hinge sensor
data/document/          – PDF import, page rendering + cache, Room entities
data/session/           – session log, signature, signed-PDF export
feature/library/        – document list
feature/prepare/        – single-pane reading + notes editor
feature/present/        – the two-sided mode
feature/sign/           – signature capture
feature/paywall/        – RevenueCat entitlement + paywall
```

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

## Offline

Field agents work in places with no signal. Everything except the purchase itself works fully
offline; entitlement status is cached and trusted between launches. An agent whose app locks up in a
client's living room because it couldn't reach a server does not remain a customer.

## Testing without hardware

A resizable AVD with hinge sensor support reproduces posture changes and hinge angle, and the
emulator's virtual sensor panel can drive the fold through its full range. That covers layout, mode
transitions and the leak guarantee. It does **not** cover IAP, real-world legibility, or how the
rotation reads to an actual second person — all of which need the borrowed device.
