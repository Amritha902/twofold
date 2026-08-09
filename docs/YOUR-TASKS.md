# Things only Amritha can do

Everything here needs an account, a legal identity, a payment method, or physical hardware — which
is why it isn't done already. Ordered by **lead time**, not by importance: the top two have external
queues attached and everything else has slack.

Last checked against the rules page: **2026-08-09**. Submissions close **Sep 30, 2026, 11:45pm PDT**.

---

## 1. Galaxy Store — ✅ **submitted 2026-08-09, now waiting**

Seller portal shows *Type of Sales: Commercial Distribution Request in Progress*. The request is with
a Samsung reviewer and the up-to-10-business-day clock is running. Nothing more to do until the
approval email arrives. Ship-by is Sep 20 to leave a store-review buffer.

**When the approval email lands, three things unlock at once:**
1. Create the Galaxy app configuration in RevenueCat and swap the `test_` key for the `galx_` one.
2. Set a **free trial** on the Galaxy products — the submission requires a trial or promo code so
   judges can test premium features, and it cannot be configured on Test Store products.
3. Make the real purchase on the borrowed foldable.

**Use the License Tester setting for that purchase.** The seller portal's own note: a registered
License Tester buying paid content in an app under beta test is not charged. Add the Samsung account
on the borrowed device there, and the required real purchase costs nothing.

**Have ready before you start:**
- PAN card
- Government photo ID (Aadhaar or passport)
- Bank account number + IFSC, in your own name
- A business/contact address and phone number
- Your email

**Cost: nothing.** Verified against Samsung's own FAQ on 2026-08-09, which says outright: *"No,
there is no sign-up nor annual fee to publish in Galaxy Store."* For comparison, Apple charges
$99/year and Google Play $25 one-off.

The **Financial Information** step is not a payment page and reads like one. "Settle the revenue
generated in the Galaxy Store" means *pay you*. Minimum Remittance is the smallest amount Samsung
will bother wiring; Payment Account is where your earnings land. Nothing is charged.

**Samsung's cut, when you do earn:** 15% on subscriptions — you keep **85%**. Paid apps and one-off
in-app items are 20/80. Apple and Google both take 30% in year one, so this is a genuine advantage
and belongs in the HAMM award pitch rather than sitting in a footnote.

**Time:** ~20 minutes of form-filling, then you wait.

> This is the one item I will not do even with full access to your machine. It is your ID and your
> bank details being submitted as a legal declaration that you are the seller. Not a permissions
> problem — it genuinely has to be you.

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

**File:** `docs/media/twofold-demo.mp4` — 34s, already inside the 2-minute limit.

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

**Free trial is blocked, not forgotten.** Pricing and trial period are read-only on Test Store
products — a trial belongs to the real store product. It lands with the Galaxy config in item 1.

---

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
