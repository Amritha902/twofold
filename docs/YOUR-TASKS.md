# Things only Amritha can do

Everything here needs an account, a legal identity, a payment method, or physical hardware — which
is why it isn't done already. Ordered by **lead time**, not by importance: the top two have external
queues attached and everything else has slack.

Last checked against the rules page: **2026-08-09**. Submissions close **Sep 30, 2026, 11:45pm PDT**.

---

## 0. Reconnect Gmail — **blocks me from reading any access emails** (2 minutes)

You said new accesses arrived by email. I cannot read them: the Gmail connector returns
*"This connector requires additional permissions. The user needs to reconnect it with the
appropriate access."*

Reconnect it in **Settings → Connectors → Gmail** and grant read access. Until then, forward or
paste anything that arrives — approval emails from Samsung, RevenueCat, OneSignal or Devpost all
unlock work that is otherwise stalled, and I have no way to see them.

---

## 1. Galaxy Store — ❌ **rejected 2026-08-12**

Samsung refused the commercial seller application. **Best App for Galaxy is out**, and the cost of
that is smaller than it feels: checked twice against the Devpost prize text, that award lists **no
cash figure at all** — its prize is a Times Square billboard, a trophy, and three weeks of featured
placement on the Galaxy Store. Every award with money attached is still open.

**Two things to do, in this order:**

### a) Paste me the rejection reason

I cannot read it — the Gmail connector needs reconnecting (task 0). Samsung rejections at this stage
are usually one of a small set, and most are re-submittable:

- name on the bank account not exactly matching the PAN or the government ID
- an address on the application differing from the address on the ID
- a business-registration document expected for "Commercial" that an individual seller cannot supply
- an unreadable or expired ID scan

If it is any of the first three, a corrected re-application is worth 20 minutes even now — the
Galaxy award is not worth cash, but a second store listing costs nothing to have.

### b) Decide on the $25 Google Play account — **this is the real decision**

Every cash award except Next Gen requires a published app. Play is the only store still open to us.

| | Without Play | With Play |
|---|---|---|
| Awards we can enter | **1** (Next Gen) | **9** |
| Largest prize reachable | $15,000 | $100,000 |
| Cost | ₹0 | **$25 one-off** |
| Time to live | — | usually under 48 hours |

Everything on our side is ready for it. `BillingKey` already accepts a `goog_` key and routes it
through the plain configuration rather than `GalaxyConfiguration`, both RevenueCat artifacts are
already in the build, and the path is unit-tested. The full listing copy, graphics list and data
safety declarations are written up in `docs/store/PLAY-LISTING.md`. It is a key swap and a form.

> Still the one thing I will not do for you: it is your ID and your card, submitted as a legal
> declaration that you are the developer. Not a permissions problem — it has to be you.

---

## 1b. Next Gen Award — now our primary entry, and it needs no store at all

Student-only, **$15,000 first prize**, and the rules remove the blocker that just hit us:
*"submitting a video and open-source code"*, explicitly *"removing the requirement for a paid Apple
or Google developer account."*

- The repo is already public, so the open-source half is done.
- You will likely need proof of active student enrolment — have a VIT ID or enrolment letter ready.
- The video requirement is the same one we already owe Devpost, so this costs almost nothing extra.

This is the entry that survives every remaining blocker. Treat the Play decision as upside on top
of it, not as a prerequisite.

---

## 2. Send the RevenueCat email — today

**The draft is in [`revenuecat-email.md`](revenuecat-email.md)** — copy the block, fill in the
Devpost handle, send. Three questions in it, and each answer changes the schedule rather than just
the paperwork. The one that matters most is Q2: if RevenueCat Ads counts as an alternative to a
purchase, the store seller account stops being a hard dependency at all.

**To:** shipaton@revenuecat.com
**Time:** 2 minutes. Replies take days during a hackathon, so the cost of asking late is real.

---

## 3. Devpost account + participant registration

**Where:** https://revenuecat-shipaton-2026.devpost.com — "Join hackathon".

**Use the Devpost account you already have** — the personal one with GitHub linked. Do not create a
second account for the student entry. The Next Gen page is explicit that what it needs is *"a student
or academic email on Devpost"* — an email **on the account**, not a separate account. Two accounts
would also risk reading as duplicate entries, and the GitHub link on the existing one is worth
keeping.

**Add `amritha.s2023@vitstudent.ac.in`** as a second email under Devpost → Settings → Emails, and
verify it.

**Confirmed eligible:** Next Gen checks academic domains against JetBrains/swot, and
`vitstudent.ac.in` is listed there as *Vellore Institute of Technology, Vellore*. Checked directly
against the swot repository on 2026-08-09.

**Your Devpost handle** is the username in your profile URL — `devpost.com/<handle>`. Click your
avatar, top right, then your profile; the handle is the last part of the address. That is what goes
in the RevenueCat email.

**Next Gen prizes:** $15,000 / $10,000 / $5,000. Worth entering on its own merits, not only as a
hedge.

**Time:** 5 minutes. Do it before the video upload since you'll want the account.

---

## 4. Upload the demo video to YouTube (unlisted is fine, **public visibility required**)

The rules are explicit: the video must be **hosted on YouTube or Vimeo and publicly visible**, with
the link on the submission form. A self-hosted mp4 does not satisfy it, so the file currently on
GitHub Pages does not count.

**File:** `docs/media/twofold-demo.mp4` — 49s, well inside the 2-minute limit. Reshot to include
Flex Mode and the clause jump, so it now shows all three postures.

**Title:** `Twofold — one document, two sides of the table (Galaxy Fold)`

**Description:**
```
Twofold turns a Galaxy foldable laid flat on a table into a two-sided document reader.
The client reads one clause at a time, in their own language, and can have it read aloud.
The agent keeps the original page, their private notes, and the controls.

Built for RevenueCat Shipaton 2026 — Best App for Galaxy.
Source: https://github.com/Amritha902/twofold
```

**Do not add music.** The rules forbid third-party copyrighted material without permission, and the
video needs no narration anyway.

**Time:** 10 minutes.

---

## 5. RevenueCat — ✅ **dashboard configured 2026-08-09**

Project `635c7bd8`. Entitlement `pro` created and attached to Monthly, Yearly and Lifetime; the
`default` offering is current with three packages.

**A bug was caught doing this.** Onboarding auto-created an entitlement with the identifier
`Twofold Pro`, but the app looks up `pro`. Identifiers are immutable, so a paying subscriber would
have been shown the paywall forever with no error anywhere. Fixed by creating `pro` properly; the
auto-created one is renamed "Auto-created (unused)" and left in place — deleting it is your call.

**Still yours, 20 seconds:** copy the Test Store key from
https://app.revenuecat.com/projects/635c7bd8/apps -> "Show key" into `local.properties`, after
`REVENUECAT_GALAXY_KEY=`. The file is prepared and gitignored.

**Free trial: done.** I had this wrong — a trial is read-only *after* a product is created, but it
is settable *at* creation. So `twofold_pro_monthly` and `twofold_pro_yearly` now exist with a
one-week trial, are attached to `pro`, and are the products the `default` offering serves. Verified
in the app: the paywall reads *"Twofold Pro · 1 week free, then $5.99"*.

That closes the submission requirement that judges be able to unlock the in-app purchase and test
premium features, for the Test Store path. The Galaxy products will need the same trial set when
they are created, and it cannot be added afterwards — set it at creation.

**Dashboard checklist is at 4 of 6.** The two left are both correctly incomplete:

- *Create a real app configuration* — blocked on Samsung, nothing to do.
- *Secure your sandbox access* — currently "Anybody", which is the default and fine while the only
  store is the Test Store, because those purchases grant nothing real and earn nothing. **Tighten it
  to "Allowed App User IDs only" on the day the `galx_` key goes live**, not before: a loose sandbox
  next to a real store configuration lets anyone holding the key grant themselves Pro, and tightening
  it now would break the anonymous test purchases used to verify the billing flow.

The paywall step is ticked because the app has one — written in its own typography rather than
RevenueCat's visual editor, which would have replaced a designed screen with a generic one.

---

## 5b. OneSignal — built, but it cannot deliver until you do one thing

**Done from my side:** the app publishes `has_follow_ups` and `unsigned_documents` tags when a
meeting ends; a segment *"Agents with unsigned documents"* filters on `has_follow_ups = true`; and a
push draft *"Follow up on unsigned documents"* targets that segment — *"Still waiting on a
signature / You showed a document that was never signed. A quick call today is usually all it
takes."* Saved as a draft, not sent.

**Yours, and nothing is deliverable without it:** OneSignal logs `Missing Google Project number`.
Push needs Firebase Cloud Messaging credentials. OneSignal → Settings → Push → Android → upload the
FCM service account JSON from a Firebase project. That needs your Google account and handles a
credentials file, so it is not mine to do.

Until then the tags accumulate and the campaign sits ready. After it, the loop closes.

## 6. Book the borrowed foldable

Two separate reasons, and the second is new:

1. Galaxy Store IAP **cannot** be tested on an emulator. It needs physical hardware and a Samsung
   account.
2. The rules say the demo video *should show the app running on the device it was built for*. The
   current cut is an emulator recording. For a category judged on foldable optimization, a judge who
   notices that has a reason to mark it down.

**When:** as soon as seller verification clears — that window may be narrow. Ask the lender now for
a provisional date rather than after Samsung replies.

---

## 7. Decide the Play Store hedge — by ~Sep 5

The rules say: *"Galaxy Store exclusivity may receive bonus consideration but is not required."*

So Galaxy Store publication is required for the **Best App for Galaxy** category and nothing
substitutes for it. But if Samsung's queue looks like missing Sep 20, publishing to **Google Play as
well** keeps every other category alive — Grand Prize, HAMM, Design, Peace, #BuildInPublic — at the
cost of only the exclusivity bonus.

- Google Play developer account: **$25 one-off**, review usually inside 48 hours.
- Samsung commercial seller: ₹0, up to 10 business days.

**Decision point:** if you have no Samsung approval by around Sep 5, pay the $25 and publish to Play
in parallel. Losing a bonus is much cheaper than losing every category.

---

## 8. Interview 10 real field agents

The whole product thesis is well-argued and completely unvalidated. Insurance agents, loan officers,
anyone who explains documents across a table. Ask what they do today and what breaks, not whether
they like the idea.

No deadline, no dependency — but it's the only item here that could still change what gets built.
