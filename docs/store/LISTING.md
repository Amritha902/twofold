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
Your client reads the clause in their own language. You keep the original, and your notes.
```

## Full description

```
Millions of people sign financial documents they cannot read.

Lay your Galaxy foldable flat on the table between you and your client. The crease splits the
screen, and each half shows the form of the document that suits whoever is looking at it.

Their half shows one clause, as text, large enough to read across a table — and in Hindi, Tamil,
Bengali or Marathi if that is what they read. If they read nothing at all, it can be spoken to them
in the same language. Your half shows the whole page as printed, plus your private notes and your
talk track. Move to a clause and it appears in front of them.

They also get one button of their own: a quiet way to say they'd like a clause explained. You see it
straight away, and the clauses they asked about are written onto the signed copy — so the record
shows what was actually discussed, not only that a signature happened.

Everything happens on your phone. The policy is never uploaded anywhere.

You never hand over your phone. They never see your notes.

BUILT FOR THE WAY FIELD AGENTS ACTUALLY WORK

• Nothing is handed over. You keep the device and the conversation.
• Their language, not just yours. On-device translation into Hindi, Tamil, Bengali and Marathi.
• Read out loud. For the clients who cannot read the document in any language.
• They can ask. One button on their half, and a signed copy that records what they asked about.
• Not only insurance. Loans, medical consent, tenancy, employment — the wording follows the work.
• Made to be read. One clause at a time, set large, and you can raise it further for the clients
  who left their glasses at home.
• Scanned documents work too. If a PDF has no text in it, Twofold reads the pages instead.
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

Everyone — no user-generated public content, no ads, no analytics.

**Data safety declaration.** Do not tick "no data collected". It isn't true, and it contradicts the
privacy policy, which a reviewer will read alongside it. The accurate answer is:

- **Collected:** purchase history and an app-generated anonymous ID, by the billing SDK, for app
  functionality only. Not linked to an identity, not shared with third parties for advertising, not
  used for tracking.
- **Not collected:** name, email, location, contacts, photos, files, messages, health, or any
  identifier that follows the user across apps.
- Documents, notes, signatures and the meeting log never leave the device and so are not
  "collected" in the data-safety sense.

## Support

- Support email: amritha16112005@gmail.com
- Privacy policy URL: **https://amritha902.github.io/twofold/privacy.html** — live, verified 200.

---

## Assets in this folder

| File | Size | Use |
|---|---|---|
| `01-own-language.png` | 1600×2000 | Screenshot 1 — the clause in the client's language |
| `02-two-sided.png` | 1600×2000 | Screenshot 2 — the two halves |
| `03-prepare.png` | 1600×2000 | Screenshot 3 — setting up before the meeting |
| `04-signature.png` | 1600×2000 | Screenshot 4 — signing |
| `05-they-can-ask.png` | 1600×2000 | Screenshot 5 — the client's own button |
| `promo-16x9.png` | 1920×1080 | Promotional graphic |
| `icon-512.png` | 512×512 | Store icon |
| `../media/twofold-demo.mp4` | 32s | Listing video |
| `../media/twofold-hook.mp4` | 13s | Devpost / social |

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

**Degrades cleanly, verified.** The minified release APK was installed and run on a flat-screen
emulator reporting no hinge sensor: single pane, no crash. Every foldable behaviour is an
enhancement guarded by a null check.

**Galaxy Store exclusive.** Not published on Play or the App Store.

---

## Open items before this can be submitted

1. **Commercial seller account** — not started. Free, but verification can take up to 10 business
   days. Everything else is ready and waiting on this.
2. **RevenueCat dashboard** — `pro` entitlement and the subscription product, then a real purchase
   on hardware. Galaxy Store IAP cannot be tested on an emulator.
3. **Release build** — signed with an upload key, which needs the seller account first.
