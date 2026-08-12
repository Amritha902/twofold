# Next Gen Award — the primary entry

**$15,000 first place. $10,000 second. $5,000 third.**

This became the primary target on 2026-08-12, when Samsung rejected the commercial seller
application with *"Please apply as a Corporate Commercial seller."* An individual cannot sell paid
content on the Galaxy Store, so *Best App for Galaxy* is out — and it turned out to carry **no cash
prize** anyway.

Next Gen is the award that no remaining blocker can touch. Its rules waive the exact thing that
just broke:

> "No paid Apple or Google developer account or store release is required."

Everything else in the hackathon needs a published app. This one needs a video and a repo.

---

## The four requirements

### 1. Student email on Devpost — ⚠️ **the hard gate, and it needs checking**

> "an active student enrolled in high school, college, university, bootcamp, or another academic
> program and use a qualifying student or academic email address on Devpost"
>
> "Email-domain eligibility may be verified using JetBrains/swot."

**This is the one thing that can disqualify the entry outright**, and it is not about the app at
all. Verification runs against the [JetBrains/swot](https://github.com/JetBrains/swot) domain list.

**Checked 2026-08-12: both `vit.txt` and `vitstudent.txt` exist in swot.** A VIT address will
verify.

**So: the Devpost account must be registered with the `@vitstudent.ac.in` address, not
`amritha16112005@gmail.com`.** If the account was created with the personal Gmail, change the
primary email in Devpost account settings — or the entry fails eligibility no matter how good the
app is. Do this before submitting anything.

### 2. Public repo with a detectable licence — ✅ **done**

> must "contain all necessary source code, assets, and instructions required for the project to be
> functional" and include "an open-source license file… detectable and visible at the top of the
> repository page"

Verified against the GitHub API on 2026-08-12: `Amritha902/twofold` is **public**, and GitHub
detects the licence as **AGPL-3.0** — which is what "detectable" means here; a `LICENSE` file that
GitHub cannot classify would not show the badge at the top of the page.

"Instructions required for the project to be functional" is the part worth re-reading before
submitting: a judge must be able to clone and run it. The README covers build and run; confirm it
still does once the `local.properties` key situation is settled.

### 3. Demo video, under 2 minutes — ⚠️ **the remaining work**

Same requirement as every other category, and the same gap we already had. Must be on YouTube or
Vimeo and publicly visible — a self-hosted mp4 does not satisfy it.

Uploading is yours; I cannot publish to your YouTube account.

### 4. RevenueCat SDK powering a purchase — ⚠️ **needs confirming**

> The app must still "use the RevenueCat SDK to power at least one in-app or web purchase, or that
> serves ads through RevenueCat Ads."

This requirement is **not** waived by the student category. Only the store release is.

The obvious reading is that RevenueCat's **Test Store** is the intended mechanism — it exists
precisely to make purchases work without a store account, which is the situation this category
creates by design. Our paywall already runs against it end to end.

One wrinkle, and it is ours rather than theirs: `BillingKey` deliberately refuses a `test_` key in
a **release** build, because the RevenueCat SDK crashes on launch in that configuration and their
own docs warn that such apps are rejected in review. For a Next Gen submission judges build from
source, so a debug build with the Test Store key is both safe and correct. **Do not ship a release
APK with a test key** — that path still crashes.

Worth one email to RevenueCat to confirm Test Store purchases count for this category, since the
whole entry rests on it.

---

## What is already true

- Public repo, AGPL-3.0, licence detected by GitHub
- RevenueCat SDK integrated, paywall working against the Test Store
- 62 unit tests green; reachability check wired into `gradle check` and CI
- On-device OCR, translation into 8 Indian languages, text-to-speech, and dictation — nothing
  leaves the phone
- Two-sided foldable layout driven by real `FoldingFeature` geometry, not a hardcoded split

## What is left

| Item | Owner | Blocking? |
|---|---|---|
| Confirm the Devpost account uses the VIT student email | Amritha | **Yes — eligibility** |
| Record and upload the demo video (<2 min, YouTube) | Recording: me. Upload: Amritha | Yes |
| Confirm Test Store purchases satisfy the requirement | Amritha (one email) | Probably not, but worth certainty |
| Proof of enrolment, if asked for | Amritha | Possibly |

Note that **none of these depend on the $25 Play account.** Play is worth doing because it opens
nine other awards, but Next Gen stands entirely without it.
