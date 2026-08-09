# Email to RevenueCat — Shipaton purchase requirement

**To:** shipaton@revenuecat.com
**Subject:** Does a Test Store purchase satisfy the IAP requirement? (Galaxy Store entry)

Fill in the Devpost handle before sending. If you haven't registered yet, write "registering this
week" instead — don't leave the placeholder in.

---

```
Hi Shipaton team,

I'm building a Galaxy Store–exclusive entry for Best App for Galaxy, using
purchases-store-galaxy 10.16.1 with GalaxyConfiguration. Three things I
can't resolve from the rules page:

1. Does a purchase completed through RevenueCat's Test Store count toward
   the requirement that the SDK power at least one real in-app purchase, or
   does it have to be a live Galaxy Store transaction?

   It matters for planning rather than curiosity. Galaxy Store IAP can't be
   exercised on an emulator, and selling through it requires commercial
   seller status, whose bank verification Samsung quotes at up to 10
   business days. If only a live transaction counts, my entry depends on
   Samsung's verification queue clearing before Sep 30 — I'd rather know
   that now than discover it in the last week.

2. The challenge summary says "power at least one in-app purchase or serve
   ads through RevenueCat Ads", but the formal requirements section says
   purchase only. Which governs? If RevenueCat Ads is a genuine
   alternative, that changes my plan considerably.

3. Does the same purchase requirement apply to the Next Gen Award? The
   rules say students submit a video and source code instead of a published
   store listing, with no paid developer account required, but I can't tell
   whether the purchase requirement is waived along with the store listing
   or still stands.

The app is open source if it's useful context:
github.com/Amritha902/twofold

Thanks,
Amritha S
Devpost: <your handle>
```

---

## Why each answer changes what happens

**Q1 — if live-purchase-only:** the borrowed foldable stops being a September nice-to-have and has to
be booked for the narrow window between Samsung approving you and the Sep 20 ship-by. If the Test
Store counts, the purchase requirement decouples from Samsung's queue entirely and a lot of risk
comes out of the schedule.

**Q2 — if Ads counts:** RevenueCat Ads can be integrated and served without a store seller account
at all, which would remove the hardest dependency in the whole project. Worth knowing before
spending the last three weeks on a purchase flow that may not have been necessary.

**Q3 — if Next Gen waives it:** the safety net is genuinely a safety net, and can be submitted
independently of Samsung. If it doesn't, Next Gen is not the free fallback the plan currently
assumes, and that assumption needs correcting now rather than on Sep 29.
