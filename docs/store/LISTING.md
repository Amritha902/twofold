# Galaxy Store listing — paste-ready

Everything below is final copy for the Seller Portal. Assets are in this folder.

> **Before submitting:** Samsung publishes exact asset specs only inside the Seller Portal, not in
> public docs. Everything here is built to the safe Android envelope — 24-bit PNG, every side
> between 320 and 3840px — plus a 16:9 graphic, which is the ratio Galaxy Store considers for its
> own promotions. Confirm against the portal's own field hints on first upload and re-export if it
> asks for something specific. `compose_store.py` regenerates the whole set in one run.

---

## App name

```
Twofold
```

## Short description

```
One device, two sides of the table. Show your client the document while you keep your notes.
```

## Full description

```
Lay your Galaxy foldable flat on the table between you and your client. The crease splits the
screen. Their half faces them, right-side-up. Your half faces you.

They see the document — clean, large, nothing else. You see the same document plus your private
notes, your talk track, and the controls. Point at a clause and it lights up on their side. Turn a
page and theirs turns with it. When you're done, they sign on their half.

You never hand over your phone. They never see your notes.

BUILT FOR THE WAY FIELD AGENTS ACTUALLY WORK

• Nothing is handed over. You keep the device and the conversation.
• Made to be read. Raise the type size on your client's half without touching your own — for the
  clients who left their glasses at home.
• Works with no signal. Documents live on your phone. Nothing waits on a server in someone's
  living room.
• Signatures that hold up. Every signed copy carries the time and a fingerprint of the document as
  presented, so the pages signed are provably the pages shown.
• Know who hasn't signed. Every meeting is logged, so the follow-up list writes itself.

Twofold is free to try. Pro removes the watermark from signed copies and lifts the document limit.

Twofold is not a qualified electronic signature service. A signed copy is a record of assent — the
digital equivalent of signing a printout.
```

## Category

Business / Productivity

## Keywords

```
foldable, flex mode, two-sided, document, PDF, signature, insurance agent, field sales,
client meeting, presentation
```

## Age rating

Everyone — no user-generated public content, no ads, no data collection.

## Support

- Support email: amritha16112005@gmail.com
- Privacy policy URL: *(needed before submission — see Open items)*

---

## Assets in this folder

| File | Size | Use |
|---|---|---|
| `01-two-sided.png` | 1600×2000 | Screenshot 1 — the whole idea |
| `02-prepare.png` | 1600×2000 | Screenshot 2 — the private layer |
| `03-spotlight.png` | 1600×2000 | Screenshot 3 — pointing at a clause |
| `04-signature.png` | 1600×2000 | Screenshot 4 — signing |
| `promo-16x9.png` | 1920×1080 | Promotional graphic |
| `icon-512.png` | 512×512 | Store icon |
| `../media/twofold-demo.mp4` | 30s | Listing video |
| `../media/twofold-hook.mp4` | 11s | Devpost / social |

All captures are from the app running on a folding device, at native resolution, with a clean
status bar. Nothing is mocked up.

---

## Galaxy optimization statement

*(Pasted into the submission field. 20% of the category score.)*

Twofold is not a phone app with foldable support added. Remove the fold and there is no product —
the entire interaction is one screen serving two people facing opposite directions.

**Posture drives the app, not the layout.** Twofold reads `FoldingFeature` through
`WindowInfoTracker` and switches whole modes rather than reflowing a grid. Folded or held, it is a
private single-pane editor. Half-opened and standing, it is a presenter view. Open and lying flat,
it becomes a two-sided device. The agent never presses "present" — they put the phone down.

**Flat alone is not enough, and this is the detail most foldable apps miss.**
`FoldingFeature.State.FLAT` is reported both when the device lies on a table *and* when it is fully
open in someone's hands. Entering two-sided mode in the second case would rotate the agent's
private notes toward whoever is standing behind them. Twofold additionally requires `TYPE_GRAVITY`
to confirm the device is face-up before it will split the screen.

**The split comes from the real crease.** The two panes are sized from
`FoldingFeature.bounds.centerY()` against the current window metrics — never a hardcoded 50%.
Foldables do not all crease at the midpoint, and a wrong split cuts the client's half through the
middle of a sentence. Where the crease position cannot be determined, Twofold returns null and falls
back to a single pane rather than guessing.

**The far pane is counter-rotated 180°** so it reads correctly to the person opposite, verified on
device including touch capture for the signature.

**Hinge angle drives the transition.** `Sensor.TYPE_HINGE_ANGLE` animates the mode change from the
hardware's own motion rather than a fixed-duration curve, and degrades silently where absent.

**Posture is debounced.** Physically opening a foldable emits a burst of intermediate states.
Twofold requires the posture to settle before switching, so the UI doesn't thrash at the moment
someone is watching.

**Galaxy Store billing, natively.** Purchases run through RevenueCat's `purchases-store-galaxy`
module using `GalaxyConfiguration` — not the generic configuration, which compiles identically and
then silently routes to Play Billing. Entitlement is cached and honoured offline, because agents
work where there is no signal.

**Degrades cleanly.** The same APK runs on flat-screen Galaxy phones. Every foldable behaviour is an
enhancement guarded by a null check.

**Galaxy Store exclusive.** Not published on Play or the App Store.

---

## Open items before this can be submitted

1. **Commercial seller account** — not started. Free, but verification can take up to 10 business
   days. Everything else is ready and waiting on this.
2. **Privacy policy URL** — required field. Twofold collects nothing and sends nothing off-device,
   so this is a short page, but it has to exist at a public URL.
3. **RevenueCat dashboard** — `pro` entitlement and the subscription product, then a real purchase
   on hardware. Galaxy Store IAP cannot be tested on an emulator.
4. **Release build** — signed with an upload key, which needs the seller account first.
