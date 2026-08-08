# Twofold — Submission Copy

> **Galaxy Store listing copy and assets have moved to [store/LISTING.md](store/LISTING.md)**, which
> is the paste-ready version with the finished screenshots beside it. This file keeps the Devpost
> write-up and the demo-video plan. Two copies of the same copy is how they drift apart.

---

## Galaxy Store listing

### Short description

One device, two sides of the table. Show your client the document while you keep your notes.

### Full description

Lay your Galaxy foldable flat on the table between you and your client. The crease splits the
screen. Their half faces them, right-side-up. Your half faces you.

They see the document — clean, large, nothing else. You see the same document plus your private
notes, your talk track, and the controls. Point at a clause and it lights up on their side. Turn a
page and theirs turns with it. When you're done, they sign on their half.

You never hand over your phone. They never see your notes.

**Built for the way field agents actually work**

- **Nothing is handed over.** You keep the device and the conversation.
- **Made to be read.** Raise the type size on your client's half without touching your own — for the
  clients who left their glasses at home.
- **Works with no signal.** Documents live on your phone. Nothing waits on a server in someone's
  living room.
- **Signatures that hold up.** Every signed copy carries the time and a fingerprint of the document
  as presented, so the pages signed are provably the pages shown.
- **Know who hasn't signed.** Every meeting is logged, so the follow-up list writes itself.

Twofold is free to try. Pro removes the watermark from signed copies and lifts the document limit.

---

## Galaxy optimization statement

*(This is the field the category scores. Be specific; name the APIs.)*

Twofold is not a phone app with foldable support added. Remove the fold and there is no product —
the entire interaction is one screen serving two people facing opposite directions.

**Posture drives the app, not the layout.** Twofold reads `FoldingFeature` through
`WindowInfoTracker` and switches whole modes rather than reflowing a grid. Folded or held, it is a
private single-pane editor. Half-opened and standing, it is a presenter view. Open and lying flat,
it becomes a two-sided device. The agent never presses "present" — they put the phone down.

**Flat alone is not enough, and this is the detail most foldable apps miss.**
`FoldingFeature.State.FLAT` is reported both when the device lies on a table *and* when it is fully
open in someone's hands. Entering two-sided mode in the second case would rotate the agent's private
notes toward whoever is standing behind them. Twofold additionally requires `TYPE_GRAVITY` to
confirm the device is face-up before it will split the screen.

**The split comes from the real crease.** The two panes are sized from
`FoldingFeature.bounds.centerY()` against the current window metrics — never a hardcoded 50%.
Foldables do not all crease at the midpoint, and a wrong split cuts the client's half through the
middle of a sentence. When the crease position cannot be determined, Twofold returns null and falls
back to a single pane rather than guessing.

**The far pane is counter-rotated 180°** so it reads correctly to the person opposite, with touch
handling verified through the rotation for signature capture.

**Hinge angle drives the transition.** `Sensor.TYPE_HINGE_ANGLE` animates the mode change from the
hardware's own motion rather than a fixed-duration curve — and degrades silently on devices that
don't report it.

**Posture is debounced.** Physically opening a foldable emits a burst of intermediate states.
Twofold requires the posture to settle before switching, so the UI doesn't thrash at exactly the
moment someone is watching.

**Galaxy Store billing, natively.** Purchases run through RevenueCat's `purchases-store-galaxy`
module using `GalaxyConfiguration` — not the generic configuration, which compiles identically and
then silently routes to Play Billing. Entitlement is cached and honoured offline, because agents
work where there is no signal.

**Degrades cleanly.** The same APK runs on flat-screen Galaxy phones. Every foldable behaviour is an
enhancement guarded by a null check; nothing above is a requirement to launch.

**Galaxy Store exclusive.** Not published on Play or the App Store.

---

## Demo video (10 seconds, no voiceover)

1. A document open on a Fold, held normally.
2. The phone is laid flat on a table between two people.
3. It splits. The person opposite starts reading.
4. Cut to their side of the table — the page is right-side-up to them.
5. The agent's half, showing notes the client cannot see.

No narration. If it needs explaining, the product has failed.

---

## Devpost write-up — outline

- **The problem.** Field agents explain documents across small tables all day. Today that means a
  laptop that doesn't fit, a printout that's out of date, or turning the phone around and reading
  upside-down while the client scrolls away.
- **What it does.** One device, two audiences, deliberately unequal information.
- **What's genuinely new.** Foldables can already show two people the *same* thing — Samsung's own
  Interpreter does. Twofold is the first to show them *different* things, and to make the difference
  a structural guarantee rather than a setting: the client renderer takes a type with no field for
  private data, enforced by a test.
- **What I'd do next.** The client's half in the client's own language, while the agent works from
  the original. On-device, offline. Millions of people sign financial documents they cannot read.
