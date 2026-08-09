# Twofold — Shipaton 2026 Plan

## Dates

| | |
|---|---|
| Submissions close | **Sep 30, 2026, 11:45pm PDT** |
| Judging | Oct 1–13, 2026 |
| Winners announced | Oct 21, 2026 |
| **Our ship-by** | **Sep 20, 2026** — Galaxy Store review buffer |

Planning started Aug 8, 2026. Roughly six usable weeks at a few hours per week.

## Eligibility — the three that can disqualify us

1. **First public release must land inside Aug 1 – Sep 30, 2026.** An update to an already-released
   app is ineligible. Twofold is new, so this is satisfied, but it means nothing ships early "just to
   test the pipeline" under a different listing.
2. **RevenueCat SDK must power at least one real in-app or web purchase.** Not a mock paywall.
3. **Galaxy category requires publication on the Samsung Galaxy Store**, a live listing URL, and a
   written description of the Galaxy optimization. 20% of the category score is exactly that.

## Critical path

These are ordered by lead time, not by importance. The first two are long-lead and gate everything.

| # | Item | Lead time | Owner |
|---|---|---|---|
| 1 | Galaxy Store **commercial seller** registration | Several days + up to 10 business days for bank verification | Amritha — needs ID and bank details, cannot be delegated |
| 2 | Devpost registration + participant form | Immediate | Amritha |
| 3 | Borrowed foldable, booked for early-mid September | Depends on the lender | Amritha |
| 4 | Toolchain + foldable AVD | Hours | Claude |
| 5 | Everything else | Six weeks | Claude |

**Item 1 is the real deadline.** Selling IAP requires commercial seller status, and the bank
verification alone can consume two weeks. Starting it in September means not shipping.

**Item 3 is not optional.** Galaxy Store in-app purchases cannot be tested on an emulator — they
require a physical Galaxy device signed into a Samsung account. The purchase flow is built against
RevenueCat's Test Store, but it must be validated for real on hardware before submission.

## Six-week build

| Week | Dates | Outcome |
|---|---|---|
| 0 | Aug 8–10 | Registrations filed, toolchain up, repo live, agent interviews begin |
| 1 | Aug 11–17 | Posture engine + two-pane split running in the emulator — **the core demo exists** |
| 2 | Aug 18–24 | PDF import, page render + cache, synced page turn and zoom |
| 3 | Aug 25–31 | Private notes, talk track, text extraction, clause reader, legibility |
| 4 | Sep 1–7 | Signature capture, signed PDF export, session history |
| 5 | Sep 8–14 | RevenueCat + paywall, full design pass, **borrowed-device window: real IAP test** |
| 6 | Sep 15–20 | Store assets, listing copy, Galaxy Store submission |
| — | Sep 20–30 | Review buffer, Devpost write-up, launch posts |

Week 1 is deliberately front-loaded with the thing that is hardest to fake. If the two-sided split
doesn't feel right on real hardware, we need to know in week one — not week five.

## Category strategy

One app, several categories. Primary target first, then whatever is nearly free.

| Category | Effort | Notes |
|---|---|---|
| **Best App for Galaxy** | Primary | The whole thesis. Thin field — most of ~14k entrants ship iOS/Play. |
| Grand Prize ($100K) | Free | Automatic consideration |
| HAMM Award | Free | Judged on articulating the revenue model — that's the pitch we already have |
| #BuildInPublic | Low | Post the build weekly; costs minutes |
| Design Award | Low | Follows from doing DESIGN.md properly |
| OneSignal ($45K) | Low | Genuinely useful here: nudge the agent when a presented document went unsigned |
| Stripe web-to-app ($30K) | Medium | A web funnel selling Pro. Only if weeks 1–4 land on time. |

Galaxy Store **exclusivity earns bonus consideration** and costs us nothing, since Play and App Store
aren't in scope for this timeline. Ship Galaxy-only.

## The submission itself

The Galaxy-optimization write-up is 20% of the category score, so it gets written properly rather
than in the last hour. It should name specifics: `FoldingFeature` bounds driving a real layout split
rather than a hardcoded half, hinge-angle-driven transition, posture-selected modes, and a client
pane that is structurally incapable of rendering private content.

The demo video is ten seconds of the actual thing: a document open, the phone laid flat on a table,
and a second person reading it right-side-up. No voiceover needed.
