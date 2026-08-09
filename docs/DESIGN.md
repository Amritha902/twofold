# Twofold — Design

## The idea the design has to carry

An agent is going to put this on a table in front of someone who is deciding whether to trust them
with money. The far half of the screen is, for those ten minutes, the agent's face.

So the client pane should not look like an app. It should look like a well-set page. Every design
decision below follows from that.

## Rules

1. **Paper, not screen.** Warm off-white ground, ink-dark text, real margins. If it could be
   mistaken for a printed page across a table, it's right.
2. **No gradients, no glass, no glow, no purple.** None. This is the failure mode of AI-designed
   interfaces and it reads as cheap to exactly the person we're selling to.
3. **One accent, used rarely.** A seal red. It marks the signature line and nothing else competes
   with it.
4. **The client pane has no chrome.** No toolbar, no tab bar, no branding. Controls live on the
   agent's half. The far half is the document and only the document.
5. **Type does the work.** Hierarchy comes from size, weight and space — never from boxes, shadows
   or coloured pills.
6. **Legible at arm's length, to someone over fifty, in bad light.** The client half's base size is
   larger than any phone UI would normally use, and the legibility control goes further still.

## Palette

| Token | Value | Use |
|---|---|---|
| `paper` | `#F6F3EE` | Ground, both panes |
| `paperRaised` | `#FFFDF9` | The document sheet itself |
| `ink` | `#1B1917` | Primary text |
| `inkMuted` | `#6E675C` | Secondary, metadata |
| `rule` | `#E0D9CE` | Hairlines, margins, the crease line |
| `seal` | `#7A3428` | Signature line, the one accent |
| `marker` | `#E8B84B` @ 28% | Highlight wash on the client pane |
| `note` | `#2F4B3F` | Private notes — agent pane only |

Dark mode is a warm dark (`#17150F` ground, `#EDE7DC` ink), not a black-and-purple inversion. A
document tool used in the evening in someone's living room needs it.

## Type

| Role | Face | Notes |
|---|---|---|
| Document chrome, headings (Latin) | **Source Serif 4** | Editorial, credible, reads as printed matter |
| Document chrome, headings (Devanagari) | **Noto Serif Devanagari** | Same role, for Hindi |
| UI, controls, metadata | **Inter** | Neutral, gets out of the way |

All three are open-licensed and shipped in the APK — no network fetch, because the app works
offline.

**Why a second serif.** Source Serif 4 contains no Devanagari at all, so Hindi headings fell back
to the system sans and quietly lost the serif/sans split the whole design rests on — the thing that
makes the client's half read as paper and the agent's controls read as machinery. Compose selects a
family member by weight and style, not by script coverage, so the two serifs cannot live in one
family; the theme picks between them by locale.

Sans stays Inter in both, because Android's own fallback resolves Devanagari to a neutral sans that
sits beside Inter without a visible seam. Only the serif needed solving.

## The crease

Do not hide it and do not decorate it. A single hairline in `rule`, aligned to the physical fold,
with generous space either side so nothing important sits in the hinge. The crease is the product's
central metaphor; treating it as a defect to be masked would be the wrong instinct.

## Motion

Almost none. Two exceptions, both earning their place:

- **Entering Twofold mode.** As the device flattens, the client half settles into place — driven by
  the real hinge angle, not a fixed-duration animation. This is the moment that sells the product and
  it should feel like the hardware doing it, not the software.
- **A cast highlight.** A single soft pulse on the client pane so the client's eye finds it. One
  pulse. It never repeats.

Nothing else moves.
