# MediVault: Pitch Deck Outline, Scripts and Viva Question Bank

> **Labels:** **[REPO]** documented for `origin/main` @ `d80c1bf` (README and PR #1; this code was not run or independently tested in this work) · **[V]** official source, fetched and read · **[V-sec]** secondary source, or an official source seen only in search results · **[A]** assumption · **[P]** pending customer validation · **[PROP]** proposal
>
> **Product baseline:** GitHub `CodesbyRohit/MediVault`, `origin/main` @ `d80c1bf` (merge of PR #1, 2026-10-12).
> **Status:** This is a provisional structure for the Entrepreneurship Development (ED) mini-project by Rohit Srivastava. It is not the official RGIT template. Source IDs (S#) refer to [`06-source-register.md`](06-source-register.md).

## 1. Pitch deck outline (11 slides; matches `MediVault-Pitch-Deck.pptx`)

| # | Slide title | Key content |
|---|---|---|
| 1 | MediVault | Personal medical-record organiser · ED mini-project · Rohit Srivastava, RGIT Mumbai · Baseline `d80c1bf` · Fictional data only |
| 2 | Reports end up scattered | Reports are scattered across paper, the phone gallery, WhatsApp and portals. They can be hard to find when a doctor asks [A]. How often and how badly this happens is **unknown** [P]. |
| 3 | Who we test first | H2a (repeated diagnostic tests) is the provisional working hypothesis. H1 (Mumbai caregivers) is tested in parallel. Scorecard subtotal 15 vs 12, which is provisional and not a measurement. |
| 4 | What the product does today | Records you can create, edit, delete and search, each with a PDF attached; owner-only access; 100 MB quota; author-reported tests 496/516 passed [REPO, S2] |
| 5 | Security, honestly | New PDFs are encrypted at rest (Tink AES-256-GCM). The server holds the key. **Not end-to-end; database not encrypted.** If the keyset is lost, encrypted PDFs are unreadable. Local only. [REPO, S3] |
| 6 | The alternatives are strong | Direct: ABHA, DigiLocker, Eka Care (free app plus paid in-app Eka Pass), Apollo 24\|7. Indirect: lab apps, Google One (15 GB free), iCloud (5 GB free), WhatsApp, paper [S6–S14] |
| 7 | Positioning | A focused, ad-free, provider-independent organiser. Differentiation is thin and easy to copy. "Find a report in under a minute" is a promise to test. |
| 8 | Business model (hypothesis) | Freemium. One Family plan at an illustrative ₹99/month (hypothesis). No ads and no data monetisation. B2B only later. |
| 9 | The numbers (illustrative) | F ≈ ₹22,300/month including founder time. Break-even 335 paying families (paying-only) or 483 (5% conversion). Year one: −₹83,202 cash; −₹3.01 lakh economic |
| 10 | How we will find out | 20 exploratory conversations, past behaviour only, plus a usability task with fictional PDFs. Proposed thresholds: ≥ 5 of 10 report a problem and ≥ 3 of 10 say their tool failed. 90-day plan. |
| 11 | Risks and next steps | Demand, free alternatives, willingness to pay, keyset loss, legal verification. Next: interviews, primary-source legal checks, then the build decision. |

## 2. Three-minute pitch script (about 400 words)

> **(Slide 1)** Good morning. I'm Rohit Srivastava, and this is MediVault, a personal medical-record organiser I built for my Entrepreneurship Development project.
>
> **(Slide 2)** Most of us have medical reports on paper, in our phone gallery, on WhatsApp and in different lab apps. When a doctor asks for last year's report, it can be hard to find. I have to be honest: I don't yet know how often that really happens, or how much it costs people. That is what I'm testing.
>
> **(Slide 3)** I'm starting with people who have had repeated diagnostic tests in the past year, because they collect the most reports. In parallel, I'm testing family caregivers who manage a parent's reports. My scorecard leans towards the first group only because it fits what the product can do today. It isn't customer proof.
>
> **(Slides 4 and 5)** Today MediVault lets a user create, edit, search and filter their records and attach PDFs. Only the owner's account can open them. New PDFs are encrypted at rest, but the server holds the key. It is not end-to-end encrypted and the database is not encrypted. It runs on one computer and uses fictional data only.
>
> **(Slide 6)** The competition is serious. ABHA and DigiLocker are free government services. Eka Care and Apollo have health-record apps. Google gives 15 GB free. So I don't claim to be unique.
>
> **(Slides 7 and 8)** My position is a focused, ad-free organiser that is not tied to any hospital. The model is freemium, with one possible paid Family plan, for example ₹99 a month. That price is a hypothesis I still have to test.
>
> **(Slide 9)** In my illustrative model, a real service would cost about ₹22,300 a month, mostly founder time and security testing rather than servers. It would need roughly 335 paying families to break even, or about 483 once free users are counted.
>
> **(Slides 10 and 11)** So my next step is not more code. It's 20 short conversations about how people actually found, or failed to find, their last report, plus a usability task with fictional documents, with pass and fail rules set in advance. If the evidence says no, I'll report that honestly. Thank you.

## 3. Five-minute ED presentation script

Use the 3-minute script and add these four expansions. Each takes about 30 to 45 seconds.

1. **After slide 3, the scorecard:**
   - "I scored both segments on eight criteria, with equal weights. Two criteria, severity and willingness to pay, are marked *unknown* because no source measures them.
   - The first group scores 15 out of 30 on the six I could score, against 12 for caregivers. The gap comes from product fit and privacy risk.
   - If interviews show caregivers ahead on both unknowns, I switch."
2. **After slide 6, why people might still choose MediVault:**
   - "A gallery or Drive folder has no dates, types or providers. ABHA links records from participating facilities.
   - MediVault keeps a structured, searchable personal history of any report the user uploads, without needing an ABHA address. Whether people value that is exactly what the interviews test."
3. **After slide 9, how break-even is calculated:**
   - "Each paying family contributes ₹99 minus a ₹2.34 payment fee minus ₹30 of support time, which leaves ₹66.66.
   - ₹22,300 divided by ₹66.66 gives about 335 families.
   - With 19 free users for every paying family, the contribution drops to about ₹45, so I need about 483.
   - In the year-one scenario, with ten more families a month and no churn, operating cash turns positive in month six, but the year still ends ₹83,202 down in cash."
4. **After slide 11, legal context:**
   - "The DPDP Rules were notified in November 2025, and the core duties reportedly start in May 2027.
   - I've marked every legal point for primary-source verification, and I make no compliance claim."

## 4. Viva question bank (24 questions)

| # | Question | Concise, evidence-based answer |
|---|---|---|
| 1 | Why does this need to exist when free cloud drives exist? | It may not, and that is what I'm testing. Google One gives 15 GB free [S13] and iCloud 5 GB [S14], but they store files without medical structure. MediVault stores each report with its date, type and provider, and lets you search them [REPO]. Whether that is worth switching for is [P]. |
| 2 | Why would anyone trust a student-built health-record app? | Today they shouldn't trust it with real data, and I don't ask them to. The prototype uses fictional data only [REPO]. Before any real use I propose hardening, an independent security test and a lawyer-reviewed privacy notice [PROP]. |
| 3 | Who exactly is the first paying customer? | I don't have one. My hypothesis is a person managing reports for several family members, through a Family plan [A]. In interviews I only ask what people already pay for, not whether they would pay. |
| 4 | How do you calculate break-even? | Contribution per family Cₚ = ₹99 − ₹2.3364 fee − ₹30 support = ₹66.66. Break-even = F ÷ Cₚ = ₹22,300 ÷ 66.66 ≈ 335 families. Including free users at an illustrative 5% conversion, it is about 483 (`04-financial-model.md`). |
| 5 | What evidence supports your target market? | No secondary source measures demand for this product. I have context only: an ageing population [S24] and gaps in ABHA awareness [S26, S27]. The segment choice is a provisional working hypothesis, based mainly on product fit. |
| 6 | How will you get users without a marketing budget? | For validation: the screener and personal and campus networks, with no paid ads [PROP]. Growth beyond that through referral is only a hypothesis [A]. |
| 7 | What happens if a patient loses the encryption key? | Patients don't hold a key. The server holds the keyset [S3]. If the *operator* loses it, every encrypted PDF becomes permanently unreadable [S3]. So I propose two tested offline copies of the keyset, kept apart from the data backups [PROP]. |
| 8 | Is MediVault end-to-end encrypted? | No. New PDFs are encrypted at rest on the server. The database, including titles and notes, is not encrypted, and the server can decrypt documents in order to serve them [S3]. |
| 9 | How is the business sustainable if most users stay free? | Free users cost support time and storage. At 2% conversion, if each free user costs ₹3 in support, the model never breaks even. That is why conversion and support time are the key sensitivities (`04-financial-model.md` §5–6). |
| 10 | Why should a hospital partner with you? | Right now it shouldn't, and I'm not asking. Hospitals are not my customers. Any partnership is a later hypothesis that needs legal review and, for ABDM, certification [S23]. |
| 11 | What stops a bigger competitor from copying it? | Nothing, today. The features are easy to copy. Any advantage would come from a validated niche, usability and trust, all untested [P]. |
| 12 | How are you different from ABHA or DigiLocker? | They are free government services that link records from participating facilities and allow consent-based sharing [S6, S8]. MediVault is a standalone organiser that needs no ABHA address. It currently lacks sharing and phone access [REPO]. |
| 13 | Isn't Eka Care already doing this, including family profiles? | Largely, yes. Its App Store listing offers "profiles for yourself and your family", ABHA generation and record storage. It also sells an in-app Eka Pass ($2.49/month on the US-dollar store; INR price unverified) [S9]. It is a direct competitor that already has a paid tier, which is one reason the Family plan is unvalidated. |
| 14 | Why ₹99 a month? | It's a hypothesis that sits between Google One's ₹59 (30 GB) and ₹130 (100 GB) plans [S13] and near iCloud's ₹75 (50 GB) plan [S14]. Those are storage products, so they only help anchor the price. They don't prove anyone would pay. |
| 15 | Did you value your own time? | Yes. Founder time is ₹200–₹400 an hour [A]. In the base case it is ₹12,000 of the ₹22,300 monthly fixed cost. Year one needs about 727 hours, and the economic loss is ₹3,01,422 against ₹83,202 in cash. |
| 16 | Are your projections a forecast? | No. Ten more paying families a month, no churn and no paid marketing is an illustrative scenario. No customer count, conversion or willingness-to-pay figure comes from evidence. |
| 17 | Is MediVault legally compliant with the DPDP Act? | I make no compliance claim. The DPDP Rules 2025 were notified in November 2025, with core duties reportedly from 13 May 2027 [S18, S20]. I list these as awaiting primary-source verification (`05-legal-privacy-risk.md`). |
| 18 | Will you integrate with ABDM? | Not now. Reportedly, it would need the sandbox, functional milestones and a security audit by a CERT-In-empanelled auditor [S23, low confidence; to verify with NHA]. It's a future proposal. |
| 19 | How do you avoid leading questions in interviews? | I ask only about past behaviour ("Tell me about the last time you needed an old report"). I describe the idea neutrally, and only at question 12. I never ask "would you use it?" (`03-customer-discovery-toolkit.md`). |
| 20 | Isn't 20 interviews too few? | For statistics, yes. It's exploratory, chosen to fit a semester. I report counts such as "4 of 10", never percentages, and treat the results as indicative. |
| 21 | What if the interviews show no problem? | Then I report that and reframe or stop. The decision rules are set in advance. A negative result is a valid ED outcome. |
| 22 | What does MediVault actually do today? | At `d80c1bf`: records you can create, edit, delete and search, each with PDFs (10 MB per file, 100 MB per patient); owner-only access; new PDFs encrypted at rest [REPO]. It runs locally with fictional data. |
| 23 | Was the code tested? | The author reports 516 tests: 496 passed, 0 failed, 20 skipped [S2]. They were not re-run for this business package. |
| 24 | What is the biggest cost? | People and security, not servers. Hosting is about 18% of base fixed cost; founder time plus security testing is about 76% (`04-financial-model.md`). |
