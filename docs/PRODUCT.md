# Twofold — Product Spec (v1)

## The one sentence

Lay a Galaxy foldable flat between you and your client; the crease becomes the boundary between what
they see and what only you see.

## The buyer

An insurance / loan / mutual-fund field agent in India. They sit across small tables — homes, tea
shops, bank branches, clinic waiting rooms — and walk a person through a document that person does
not want to read. Their day is: explain, reassure, answer the same four objections, get a signature.

**What they use today**

| Tool | Why it fails |
|---|---|
| Laptop | ₹40,000, doesn't fit on a café table, creates a wall between two people |
| Printouts | No notes, no signature capture, out of date the week they're printed |
| Phone, turned around | Agent reads upside-down; client can scroll away; notes are visible |
| Phone, handed over | Agent loses control of the conversation and of the device |

Every one of these is a worse solution to a problem they already pay to solve. That is the whole
business case.

## The interaction model

### Posture drives mode — automatically

| Posture | Mode | What happens |
|---|---|---|
| Folded / held in hand | **Prepare** | Single pane. Import, read, write private notes, build the talk track. |
| Open flat, lying down | **Twofold** | Split at the crease. Agent pane + client pane, opposite orientations. |
| Half-folded, standing | **Present** | Top half faces a small group; bottom half is the agent's control surface. |

The transition into Twofold mode is the product. The agent doesn't press "present" — they put the
phone down, and it becomes a two-sided device. That is the ten-second demo and it needs no narration.

### The two panes

**Client pane** (far half, rotated 180°)
- The document. Rendered large, high contrast, generous margins.
- A **Legibility** control the agent can raise — many clients are 50+ and reading a policy without
  their glasses. This is a small feature with outsized credibility.
- Highlights and spotlights the agent casts.
- The signature surface, when called.
- **Nothing else. Ever.**

**Agent pane** (near half)
- The same document, plus:
- **Margin notes** — private, anchored to a page or a region.
- **Talk track** — the three things to say on this page, and the objection that always comes up.
- Controls: page, zoom, highlight, spotlight, legibility, "ask for signature".

### The private-notes guarantee

The client pane must be structurally incapable of rendering agent content. Not hidden by a flag —
the client renderer accepts a `ClientPage` type that has no field for notes. A styling bug can leak
a colour. It must not be able to leak a note. See [ARCHITECTURE.md](ARCHITECTURE.md#the-leak-guarantee).

This is also the trust story that lets an agent use it in front of a customer at all.

### Signing

Agent taps *Ask for signature* → the client pane clears to a signature line. The client signs with a
finger. Twofold stamps the signed page with a timestamp and a hash of the document as presented, and
exports a flattened PDF. The agent pane shows a live preview of what the client is doing.

Not a qualified e-signature and v1 will not claim to be one. It is a record of assent, which is what
this workflow actually uses today when someone signs a printout.

## v1 scope

**In**
- Import PDF from device storage
- Library of imported documents
- Private margin notes and per-page talk track
- Automatic Twofold mode on flat posture, with side-swap
- Synced page turn, zoom, highlight, spotlight
- Client-side legibility control
- Signature capture and flattened signed PDF export
- Session history (what was shown, to whom, when, signed or not)
- Fully offline
- RevenueCat paywall

**Out — deliberately**
- Cloud sync, accounts, teams
- CRM integration
- Payments or premium collection
- Document editing or authoring
- Translation *(strong v1.5 candidate — see below)*

## The v1.5 feature that could matter more than all of v1

**The client pane shows a different language than the agent pane.**

The policy is in English. The client reads Tamil, Hindi, Marathi. Today the agent translates aloud,
badly, and the client signs something they never read. On-device ML Kit translation, rendered only on
the far half, means the agent works from the original while the client reads their own language.

It is out of v1 because it is a research task, not a build task, and v1 must ship. But it is the
feature that turns a good tool into one an agent will not work without — and it is the second reason
the foldable is load-bearing, since it requires two simultaneous views of one document.

## Pricing

| | |
|---|---|
| **Free** | 3 documents. Signed exports carry a Twofold watermark. |
| **Pro — ₹499/mo or ₹3,999/yr** | Unlimited documents, private notes, clean signed exports, session history. |

The paywall trigger is the export of the first signed document — the moment the agent has just
closed. Correct place to ask.

This is a daily earning tool for a commission worker, not a consumer subscription. ₹499 is
unremarkable against a single closed policy, which is why the ARPU is 10–20× a consumer app's and why
the business is credible without needing millions of installs.

## How we'll know it's real

Before the Store listing is written, ten conversations with actual agents. Not "would you use this" —
that question always returns yes. Instead: *show me the last document you walked a client through,
and show me how you did it.* If they don't reach for a workaround we've listed above, the thesis is
wrong and we should know in week one, not week six.
