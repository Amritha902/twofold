# Email to RevenueCat — Shipaton purchase requirement

**To:** shipaton@revenuecat.com
**Subject:** Does a Test Store purchase satisfy the IAP requirement? (Galaxy Store entry)

Fill in the Devpost handle before sending. If you haven't registered yet, write "registering this
week" instead — don't leave the placeholder in.

---

```
Hi Shipaton team,

I'm building a Galaxy Store entry for Best App for Galaxy, using
purchases-store-galaxy 10.16.1 with GalaxyConfiguration. Three questions
the rules page doesn't settle:

1. Can you confirm a RevenueCat Test Store purchase does NOT satisfy the
   "SDK must power at least one in-app purchase" requirement?

   I'm fairly sure it doesn't — the SDK's own validator says Test Store
   purchases are simulated, "generate no revenue", and that the SDK will
   crash if a test key is used in production. I've built accordingly
   (test keys are refused in release builds). I'd just rather have it
   confirmed than assume, because it makes Samsung commercial-seller
   approval a hard dependency with no fallback, and that verification is
   quoted at up to 10 business days.

2. The challenge summary says "power at least one in-app purchase or
   serve ads through RevenueCat Ads", but the formal requirements
   section says purchase only. Which governs?

   This is the one that would change my plan most. If RevenueCat Ads is
   a genuine alternative, it can be integrated without a store seller
   account at all, which removes the hardest dependency in the project.

3. Does the purchase requirement apply to the Next Gen Award? The rules
   say students submit a video and source code instead of a published
   store listing, with no paid developer account needed — but I can't
   tell whether the purchase requirement is waived along with the store
   listing or still stands. If it still stands, Next Gen isn't the
   store-independent fallback it reads as.

The app is open source if it's useful context:
github.com/Amritha902/twofold

Thanks,
Amritha S
Devpost: <your handle>
```

---

## Why each answer changes what happens

**Q1 — almost certainly confirms live-purchase-only.** RevenueCat's SDK validator already says Test
Store purchases are simulated and generate no revenue, so this is asking them to confirm rather than
to decide. The consequence: the borrowed foldable stops being a September nice-to-have and has to be
booked for the narrow window between Samsung approving you and the Sep 20 ship-by.

**Q2 — if Ads counts:** RevenueCat Ads can be integrated and served without a store seller account
at all, which would remove the hardest dependency in the whole project. Worth knowing before
spending the last three weeks on a purchase flow that may not have been necessary.

**Q3 — if Next Gen waives it:** the safety net is genuinely a safety net, and can be submitted
independently of Samsung. If it doesn't, Next Gen is not the free fallback the plan currently
assumes, and that assumption needs correcting now rather than on Sep 29.
