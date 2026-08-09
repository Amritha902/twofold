# Google Play listing — the hedge

**Not the plan. The fallback.** Galaxy Store publication is required for the *Best App for Galaxy*
category and nothing substitutes for it. But the rules say plainly:

> "Galaxy Store exclusivity may receive bonus consideration but is not required."

So being Galaxy-only is worth a bonus, not an entry ticket. If Samsung's commercial-seller
verification looks like missing the **Sep 20** ship-by, publishing to Play as well keeps every
non-Galaxy category alive — Grand Prize, HAMM, Design, Peace, #BuildInPublic — and costs only that
bonus.

| | Google Play | Galaxy Store |
|---|---|---|
| Account cost | **$25 one-off** | ₹0 |
| Approval | usually inside 48 hours | up to 10 business days |
| Needed for the Galaxy category | no | **yes** |

**Decision point: around Sep 5.** No Samsung approval by then, pay the $25 and publish to Play in
parallel. Losing a bonus is much cheaper than losing every category.

Everything below is ready so that decision is a day's work, not a scramble.

---

## Store listing

**App name** (30 char limit)
```
Twofold
```

**Short description** (80 char limit — 74 used)
```
Your client reads the clause in their own language. You keep the original.
```

**Full description** (4000 char limit)
```
Millions of people sign financial documents they cannot read.

Twofold turns a foldable laid flat on a table into a two-sided document reader. The crease splits
the screen, and each half shows the form of the document that suits whoever is looking at it.

THEIR HALF
One clause at a time, as text, large enough to read across a table — and in Hindi, Tamil, Bengali or
Marathi if that is what they read. If they read nothing at all, it can be spoken to them in the same
language. They also get one button of their own: a quiet way to say they would like a clause
explained.

YOUR HALF
The whole page as printed, plus your private notes and your talk track. Move to a clause and it
appears in front of them. When you are ready, they sign on their half.

You never hand over your phone. They never see your notes.

WHY A FOLDABLE
Held in your hand it is a private editor. Set it down flat and it becomes two-sided. No button is
pressed — the posture is the command. Propped half-open on a desk it becomes a workbench, with the
page above the crease and your notes and controls below it.

EVERYTHING STAYS ON THE PHONE
Translation, reading aloud and reading scanned pages all run on the device. The document is never
uploaded anywhere. It works in a living room with no signal, which is where this work actually
happens.

NOT ONLY INSURANCE
A loan officer and a borrower. A doctor taking consent. A landlord and a tenant. An employer and
someone signing an offer they half-read. Whenever one person understands a document and the other is
signing it, the two halves have work to do — the wording follows the work.

WHAT YOU GET FREE
Everything above. The free tier watermarks the signed copy; nothing is withheld from the meeting
itself.

TWOFOLD PRO
Signed copies without the watermark, as many documents as you carry, private notes and talk track on
every page, and a record of what you showed and who has not signed yet.
```

**Category:** Business
**Tags:** documents, productivity, business
**Contact email:** amritha16112005@gmail.com
**Privacy policy:** https://amritha902.github.io/twofold/privacy.html

## Graphics

| File | Size | Slot |
|---|---|---|
| `icon-512.png` | 512×512 | App icon |
| `play-feature-graphic.png` | 1024×500 | Feature graphic (required) |
| `play-phone-01.png` | 1080×2160 | Phone screenshot |
| `play-phone-02.png` | 1080×2160 | Phone screenshot |
| `play-tablet-01.png` | 2152×2076 | 7"/10" tablet — the real inner display |
| `play-tablet-02.png` | 2152×2076 | 7"/10" tablet |

**Play's screenshot rules are stricter than Devpost's** and the difference bites: the longest side
may be at most **twice** the shortest. The Devpost screenshots are 1179×2556, which is 2.17:1 and
would be rejected here — hence a separate 1080×2160 set at exactly 2:1. The Devpost assets are not
interchangeable with these.

## Data safety — declare these honestly

The form is a legal declaration, and "we collect nothing" is nearly true here but not exactly true.

**Collected:**
- **Purchase history** — RevenueCat, to know whether the agent has Pro. Required for app
  functionality. Not shared with third parties beyond the payment processor.
- **Device or other IDs** — OneSignal push token, so an agent can be reminded about documents that
  were presented and never signed. Optional; the app works fully without notification permission.

**Not collected, and worth stating because it is the product's whole argument:**
- The documents themselves. PDFs are copied into app-private storage and never uploaded.
- Extracted text, translations and speech. All on-device — ML Kit models and the platform TTS
  engine, no server round trip.
- Signatures. Flattened into a local PDF; never transmitted.
- Names typed into "who are you meeting". Free text in the agent's own log, never sent anywhere.

**Encrypted in transit:** yes, for the two items above that leave the device.
**Can users request deletion:** yes — clearing app data removes everything; there is no server-side
account to delete.

## Before publishing to Play

1. Swap `REVENUECAT_GALAXY_KEY` in `local.properties` for the **`goog_`** key from a Play app
   configuration in RevenueCat. `BillingKey` already accepts it and routes it through the plain
   `PurchasesConfiguration` rather than `GalaxyConfiguration` — the mismatch that would silently
   fail every purchase is impossible, but the key must still match the store you are shipping to.
2. Create the Play products and attach them to the **`pro`** entitlement, matching the Test Store
   ones (`monthly`, `yearly`, `lifetime`).
3. Set a free trial on the Play subscription. The submission requires judges be able to test premium
   features, and the paywall already renders a trial when one exists.
4. `applicationId` is `com.twofold` and **cannot change after first publication on either store.**
   Decide the name question before the first upload, not after.
