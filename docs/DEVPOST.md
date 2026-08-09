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
Millions sign financial documents they cannot read. Lay a Galaxy foldable flat on the table and
they read the clause in their own language, while you work from the original.
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

- **Their half** shows one clause as text, right-side-up to them, at a size readable across a
  table — and in Hindi, Tamil, Bengali or Marathi if that is what they read. Not a page image: that
  half is 130 × 63 mm, and a fitted A4 page renders 10pt type at 2pt.
- **Your half** shows the same document plus your private notes, your talk track, and the controls.
- **Move to a clause** and it appears in front of them, because it's one index, not two synced
  viewers.
- **Read it aloud** in their language, for the clients who read no language at all — which is a
  larger group than the ones translation alone reaches.
- **Raise the type size on their half only**, for the client who left their glasses at home.
- **They can ask.** One quiet button on their half — the only control that isn't yours — and the
  clauses they asked about are written onto the signed copy.
- **They sign on their half.** The signed copy carries the time and a SHA-256 of the document as
  presented, so the pages signed are provably the pages shown.

You never hand over your phone. They never see your notes.

### How I built it

Native Kotlin and Jetpack Compose. The whole product is a posture API and a PDF renderer, so a
cross-platform layer would only have got in the way of both.

- **Posture** — `WindowInfoTracker` / `FoldingFeature`, switching whole modes rather than reflowing
  a grid. Folded or held: a private editor. **Half-opened and propped on a desk: Flex Mode** — the
  page on the raised half where you are looking, your notes and controls on the flat half where your
  hands are, neither rotated because both halves are yours. **Flat on a table: two-sided**, and the
  far half turns to face the person opposite.
- **The split** comes from `FoldingFeature.bounds.centerY()` measured against live window metrics,
  never a hardcoded 50%. Where it can't be determined, the app returns null and falls back to a
  single pane rather than guessing.
- **Documents** — `PdfRenderer` behind a mutex, rendered off the main thread into an LRU cache
  sized from `memoryClass`, neighbours prefetched.
- **Billing** — RevenueCat's `purchases-store-galaxy` module with `GalaxyConfiguration`.
- **Storage** — JSON in app-private files. No Room: the entire persisted schema is smaller than one
  screen displays, and a migration framework for that is more machinery than the data deserves.

### It isn't an insurance app

The words were, for a while. The code never was. Every line does one thing: show a person a document
they have to sign, in a form they can understand, while the person explaining it keeps their own
view. That is a loan officer and a borrower, a doctor taking consent, a landlord and a tenant, an
employer and someone signing an offer half-read.

So the profession became a setting that changes the wording and nothing else — and the fact that
nothing else needed to change is the argument that it generalises, rather than a claim that it
might.

### The thing I would show a judge first

**One document, two languages, at the same instant.** The client reads clause 3 in Hindi; the agent
reads it in English, on the same screen, in the same second. That needs two surfaces facing
opposite directions, which needs a foldable.

Samsung's own Interpreter proves two-sided translation matters — but it does live *speech*, on the
cover screen. Nobody has done it for the *document*, which is the thing people actually sign. And
it only became possible after the client's half stopped being a picture of a page, because you
cannot translate a bitmap.

It runs on-device. A stranger's insurance policy is not something to upload to a translation
server.

And then the follow-on question, which is the one that actually matters: **translation only helps
someone who can read.** About a quarter of Indian adults cannot, and functional literacy for a legal
document is lower still — the exact people most likely to be sold something they don't understand.
So the clause is read out loud too, in their language, on the device. Someone who cannot read Tamil
still speaks Tamil.

Neither of those is possible with a picture of a page. You cannot translate a bitmap and you
certainly cannot speak one. The chain is the product: extract → segment → translate → speak, each
step only reachable because of the one before it.

### The one control that isn't the agent's

Everything on that screen belongs to the person selling. That is correct — it's their meeting. But a
document explained entirely by the seller, with no way for the other party to say *wait, what does
this mean*, is precisely the dynamic that produces a signature on something nobody understood.

So the client's half has one quiet button. Tapping it tells the agent immediately, and the clauses
asked about are written onto the signed copy.

That last part is the commercial argument. It turns "they signed" into "they were shown each clause,
these are the ones they asked about, and they signed after" — which is what an insurer or a bank
actually wants when a sale is disputed years later, and is not obtainable from a paper signature at
all.

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

**The product turned on an assumption nobody had measured.** I built the whole two-sided document
viewer before working out that the client's half is 130 × 63 mm — and that an A4 page fitted into
it renders 10pt type at 2pt. Unreadable. Every screenshot hid it, because a 2076px render on a
monitor is nothing like 63mm of glass across a table. It demoed beautifully and did not work.

The fix forced a better product. Text instead of a page image; one clause at a time; and, because
text can be translated where a bitmap cannot, the client's own language. The constraint was the
design.

**A Fold creases like a book, and that decides the whole layout.** `androidx.window` reports the
fold as `Orientation.VERTICAL` in the device's natural orientation — because unfolded, a Fold is a
book, and the crease runs top to bottom. The near-half/far-half geometry this product is built on
only exists once the device is turned landscape. So the app refuses to split on a vertical crease
rather than guessing, and the real usage posture is *unfolded, turned sideways, laid flat*. I found
this by being unable to reproduce two-sided mode on an emulator I had already recorded it on.

**Nothing is real until it runs.** The app compiled and passed tests for a full day before it ever
met a folding device. Three later bugs were all invisible in a screenshot and all found by driving
the thing: the client's only button sat underneath the status bar, where edge-to-edge quietly ate
every tap on it; a slow speech-service bind was being reported to the agent as a missing feature;
and every launch re-ran OCR, putting fifteen seconds of *Reading the document…* in front of a client
for a file that hadn't changed. First run, the signed PDF came out attributed to *the document's own
filename* — "Signed by Term_Life_Policy" — because a placeholder had never been replaced. No amount
of reading would have caught it.

### Accomplishments I'm proud of

**The privacy guarantee is structural, not a promise.** The client's renderer takes a `ClientPage`
type that has no field for notes and no escape hatch. It cannot render private content even if
someone wires it up carelessly later. `LeakGuaranteeTest` enforces it by reflection, and I verified
the test has teeth by mutation — adding a `note` field to `ClientPage` fails two of its four
cases. A styling bug can leak a colour; it must not be able to leak a note in front of a
paying customer.

**The posture rules are unit-testable without hardware.** They're a pure function over a `HingeState`
enum of my own rather than Android types, so the decisions the whole product turns on are covered
by seven JVM tests instead of only being checkable on a device I don't own.

**It degrades all the way down, and I checked rather than claimed it.** The minified release APK
was installed on a flat-screen emulator with no hinge sensor at all and runs single-pane with no
crash. Every foldable behaviour is an enhancement behind a null check.

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

**More languages, and better handling of scans.** Four Indian languages are in; there are twenty-two
official ones. And OCR currently reads a two-column benefits table in visual order, so it can attach
figures to the wrong clause — the agent is warned, but the honest fix is layout-aware extraction.

Before either: ten conversations with real agents. The thesis is well-argued and completely
unvalidated, and I'd rather find out in week one than after launch.

---

## Built with

```
kotlin, jetpack-compose, androidx-window, foldables, android, ml-kit, on-device-translation,
text-to-speech, ocr, revenuecat, galaxy-store, pdfbox, pdfrenderer, material3
```

## Try it out

- Source: https://github.com/Amritha902/twofold
- Project page: https://amritha902.github.io/twofold/
- Privacy policy: https://amritha902.github.io/twofold/privacy.html
- Galaxy Store listing: *(pending — see below)*

No PDF to hand? Open it and tap **See how it works** — it ships with a document whose clauses are
the instructions, read through the same extraction, translation and speech as anything else.

## Video

**[twofold-demo.mp4](https://amritha902.github.io/twofold/media/twofold-demo.mp4) — 34 seconds, no
narration.** Recorded on a foldable from an empty install, so nothing is set up off camera.

Open the document the app ships with. Say the meeting is a **tenancy** and that the client reads
**Hindi**. Set the phone down — it splits at the crease, and the clause is now in front of them, in
their language, while the original stays on the near half. Move through clauses. Press **Read** and
it is spoken aloud. The client taps the one button that is theirs, and the agent's half says *They
asked about this one — recorded on the signed copy.* Then they sign.

The last frame is the one to watch: the signature line reads **Tenant**, not "Client". The meeting
kind reaches the signed document, which is the whole "this isn't an insurance app" claim shown
rather than asserted.

A [14-second cut](https://amritha902.github.io/twofold/media/twofold-hook.mp4) of just the
transition is there too, for anywhere that won't play 34 seconds.

> **Before submitting:** the rules require the video to be **hosted on YouTube or Vimeo and publicly
> visible**, with that link on the form. A self-hosted mp4 does not satisfy it. The rules also say
> the video should show the app *running on the device it was built for* — the current cut is an
> emulator recording, so reshoot on the borrowed foldable if the device window allows.

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
| **Next Gen** (students) | No store listing required, so it clears the long-lead blocker — but the RevenueCat purchase requirement still applies, so it is not free. |
| Grand Prize | Automatic |
| HAMM Award | Free entry; the revenue model is articulated above |
| Design Award | Free entry |
| #BuildInPublic | Repo is public |

## Blocked before final submission

The Galaxy category requires a **live Galaxy Store listing URL**, and that requires a commercial
seller account whose verification can take up to 10 business days. The app, listing copy,
screenshots, promotional graphic, icon, privacy policy and demo video are all finished and waiting
on it.

**Next Gen has no store requirement** and is worth entering as a hedge rather than an afterthought.
It is not a free pass, though: the RevenueCat rule that the SDK must power at least one real
purchase applies to every category, so a Test Store purchase alone may not satisfy it. That is a
question for shipaton@revenuecat.com rather than an assumption to submit on.
