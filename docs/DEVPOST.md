# Twofold — Devpost submission

Paste-ready. Field names match Devpost's submission form.

---

## Project name

```
Twofold
```

## Elevator pitch

*(Devpost caps this around 200 characters.)*

```
Lay a Galaxy foldable flat between you and your client. The crease splits the screen — they read
the document, you keep your notes. One device, two audiences, deliberately unequal.
```

---

## About the project

### Inspiration

Watch an insurance agent explain a policy to someone at a kitchen table and you'll see the same
three workarounds every time. They turn the phone around and read upside-down. They hand it over
and lose control of the conversation. Or they carry a laptop that doesn't fit on the table and puts
a screen between two people who are trying to trust each other.

All three are workarounds for one missing thing: a screen that can face two people at once.

Foldables have had that since the first Fold, and almost nobody has used it. Google's own adaptive
design guidance describes the "two people sitting opposite, 180-degree orientation" tabletop
pattern by name. I searched the Galaxy Store, Play, and Samsung's own catalogue and could not find
a single third-party app that ships it. The platform owner documented the pattern and the field
stayed empty.

### What it does

You lay the phone flat on the table. That's the whole interface.

- **Their half** shows the document, right-side-up to them. Clean, large, nothing else.
- **Your half** shows the same document plus your private notes, your talk track, and the controls.
- **Point at a clause** by dragging on your copy — it lights up on theirs.
- **Turn a page** and theirs turns with it, because it's one page index, not two synced viewers.
- **Raise the type size on their half only**, for the client who left their glasses at home.
- **They sign on their half.** The signed copy carries the time and a SHA-256 of the document as
  presented, so the pages signed are provably the pages shown.

You never hand over your phone. They never see your notes.

### How I built it

Native Kotlin and Jetpack Compose. The whole product is a posture API and a PDF renderer, so a
cross-platform layer would only have got in the way of both.

- **Posture** — `WindowInfoTracker` / `FoldingFeature`, switching whole modes rather than reflowing
  a grid. Folded or held: a private editor. Half-opened: presenter view. Flat on a table:
  two-sided.
- **The split** comes from `FoldingFeature.bounds.centerY()` measured against live window metrics,
  never a hardcoded 50%. Where it can't be determined, the app returns null and falls back to a
  single pane rather than guessing.
- **Documents** — `PdfRenderer` behind a mutex, rendered off the main thread into an LRU cache
  sized from `memoryClass`, neighbours prefetched.
- **Billing** — RevenueCat's `purchases-store-galaxy` module with `GalaxyConfiguration`.
- **Storage** — JSON in app-private files. No Room: the entire persisted schema is smaller than one
  screen displays, and a migration framework for that is more machinery than the data deserves.

### Challenges I ran into

**`FLAT` doesn't mean "on a table."** This is the one that mattered. `FoldingFeature.State.FLAT` is
reported when a foldable is lying on a table *and* when it's fully open in someone's hands. An app
that trusts it alone will rotate your private notes toward whoever is standing behind you, upside
down, in front of a client. Twofold additionally requires `TYPE_GRAVITY` to confirm the device is
face-up before it will split the screen. That single check is the difference between a demo and
something you'd actually open in front of a customer.

**The obvious approach to two-sided is closed.** Every shipped two-sided foldable experience —
Google Translate's interpreter mode, Samsung's own Interpreter, Dual Preview — uses the cover
screen through Android's privileged dual-display mode, which third-party apps can't touch. I built
an idea on it before discovering that and had to throw it away. Splitting the *inner* screen and
counter-rotating one half is the only route actually open to a third party. It's probably also why
the field is empty: the obvious approach is closed and the available one isn't obvious.

**Building bottom-up hid dead code.** Four times I found a complete, tested-looking layer that no
screen ever reached — the session log, the spotlight, the talk-track editor, the follow-up list.
"It's built" and "it's reachable" turned out to be different claims. I ended up enumerating every
declared function in the app and checking each was referenced from a screen — which found a fifth,
a page-geometry helper written for a layout that never needed it.

**Nothing is real until it runs.** The app compiled and passed tests for a full day before it ever
met a folding device. First run, the signed PDF came out attributed to *the document's own
filename* — "Signed by Term_Life_Policy" — because a placeholder had never been replaced. No amount
of reading would have caught it.

### Accomplishments I'm proud of

**The privacy guarantee is structural, not a promise.** The client's renderer takes a `ClientPage`
type that has no field for notes and no escape hatch. It cannot render private content even if
someone wires it up carelessly later. `LeakGuaranteeTest` enforces it by reflection, and I verified
the test has teeth by mutation — adding a `note` field to `ClientPage` fails two of its three
assertions. A styling bug can leak a colour; it must not be able to leak a note in front of a
paying customer.

**The posture rules are unit-testable without hardware.** They're a pure function over a `HingeState`
enum of my own rather than Android types, so the decisions the whole product turns on are covered
by seven JVM tests instead of only being checkable on a device I don't own.

**It degrades all the way down.** The same APK runs on a flat-screen Galaxy. Every foldable
behaviour is an enhancement behind a null check.

### What I learned

**Check what's actually scored before optimising.** I burned most of a day hunting for a
conceptually novel idea, then read the rules properly: "Best App for Galaxy" scores Galaxy
optimization and store quality, with **no originality criterion at all**, and the Grand Prize scores
early release and post-launch growth. I'd been optimising for a criterion that isn't on the
scorecard, at the direct expense of two that are.

**Be precise about novelty instead of loud about it.** "The first two-sided foldable app" would get
killed in one sentence by a Samsung judge, because Samsung built one. The accurate claim is
narrower and survives contact: foldables can already show two people the *same* thing; Twofold shows
them **different** things, and makes the difference a structural guarantee rather than a setting.

**Screenshots are a design review.** Composing the store listing exposed that my spotlight dimmed
the page so hard the client could read only the lit clause — the opposite of the intent — and that
my test document was mostly empty white, which had been quietly making every screen look unfinished.

### What's next

**The client's half in the client's own language, while the agent works from the original.**
On-device, offline, both views of one document at the same moment — which is a thing only a foldable
can do. Millions of people in India sign financial documents they cannot read, while the person
selling to them translates aloud, approximately, in their own interest. That's the version worth
building.

Before that: ten conversations with real agents. The thesis is well-argued and completely
unvalidated, and I'd rather find out in week one than after launch.

---

## Built with

```
kotlin, jetpack-compose, androidx-window, foldables, android, revenuecat, galaxy-store,
pdfrenderer, material3
```

## Try it out

- Source: https://github.com/Amritha902/twofold
- Project page: https://amritha902.github.io/twofold/
- Privacy policy: https://amritha902.github.io/twofold/privacy.html
- Galaxy Store listing: *(pending — see below)*

## Video

`docs/media/twofold-demo.mp4` — 30 seconds, no narration. Held in the hand it's a private editor;
set down on the table it becomes two-sided. Then: point at a clause, ask for a signature, the
client signs, the signed copy is exported.

---

## A note on how this was built

Twofold was built with AI assistance (Claude), which the commit history shows openly rather than
hiding. Every design decision, every rejected idea, and every bug found by running it is recorded in
the commit messages — including the four dead-code layers and the signature bug that only appeared
on a real device.

---

## Categories entered

| Category | Status |
|---|---|
| **Best App for Galaxy** | Primary. Blocked on the Galaxy Store listing going live. |
| **Next Gen** (students) | Submittable now — needs only a demo video and a public repo, both of which exist. |
| Grand Prize | Automatic |
| HAMM Award | Free entry; the revenue model is articulated above |
| Design Award | Free entry |
| #BuildInPublic | Repo is public |

## Blocked before final submission

The Galaxy category requires a **live Galaxy Store listing URL**, and that requires a commercial
seller account whose verification can take up to 10 business days. The app, listing copy,
screenshots, promotional graphic, icon, privacy policy and demo video are all finished and waiting
on it.

**Next Gen has no store requirement** and is submittable independently — which is why it's worth
entering as a hedge rather than an afterthought.
