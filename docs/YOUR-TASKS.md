# Things only Amritha can do

Everything here needs an account, a legal identity, a payment method, or physical hardware — which
is why it isn't done already. Ordered by **lead time**, not by importance: the top two have external
queues attached and everything else has slack.

Last checked against the rules page: **2026-08-09**. Submissions close **Sep 30, 2026, 11:45pm PDT**.

---

## 1. Galaxy Store commercial seller registration — **do this first, today**

**Why it's first:** bank verification is quoted at up to **10 business days**, and nothing about the
Galaxy category can complete without it. Every day this waits comes off the end of the schedule, not
the start. Ship-by is Sep 20 to leave a store-review buffer.

**Where:** https://seller.samsungapps.com — register as a **Commercial Seller** (not Individual;
Individual cannot sell paid content or IAP).

**Have ready before you start:**
- PAN card
- Government photo ID (Aadhaar or passport)
- Bank account number + IFSC, in your own name
- A business/contact address and phone number
- Your email

**Cost:** ₹0. There is no registration fee — the earlier worry about a seller fee was checked and is
wrong.

**Time:** ~20 minutes of form-filling, then you wait.

> This is the one item I will not do even with full access to your machine. It is your ID and your
> bank details being submitted as a legal declaration that you are the seller. Not a permissions
> problem — it genuinely has to be you.

---

## 2. Send the RevenueCat email — today

Draft is written; three questions in it. It matters because the answer changes the *schedule*, not
just the paperwork: if only a live store purchase counts, the borrowed foldable has to be booked for
the narrow window between Samsung approving you and Sep 20.

**To:** shipaton@revenuecat.com
**Time:** 2 minutes. Replies take days during a hackathon, so the cost of asking late is real.

---

## 3. Devpost account + participant registration

**Where:** https://revenuecat-shipaton-2026.devpost.com — "Join hackathon".

**Use your student email** `amritha.s2023@vitstudent.ac.in` somewhere on the account, or add it as a
secondary address. The **Next Gen Award** is students-only and is the safety net if Samsung's queue
misses the deadline — it takes a video and open-source code instead of a published store listing.

**Time:** 5 minutes. No lead time, but do it before the video upload since you'll want the account.

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

## 5. RevenueCat dashboard — then hand it to me

Create the account and the project, then tell me and I'll do the product/offering/entitlement setup
and reconcile it against `Entitlements.kt`. You paste me the **public SDK key**; it ships inside the
APK so it isn't a secret, but I'd rather you be the one to move it.

One thing to know going in: the submission rules require **a free trial or a promo code so judges
can unlock the IAP and test premium features**. A free trial on the Pro offering is the simpler of
the two, and the app is now built to display one — see `PaywallScreen`.

**Time:** 10 minutes for you, then mine.

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
