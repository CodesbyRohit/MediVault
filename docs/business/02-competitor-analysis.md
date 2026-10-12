# MediVault: Competitor and Alternatives Analysis

> **Labels:** **[REPO]** documented for `origin/main` @ `d80c1bf` (README and PR #1; this code was not run or independently tested in this work) · **[V]** official source, fetched and read · **[V-sec]** secondary source, or an official source seen only in search results · **[A]** assumption · **[P]** pending customer validation · **[PROP]** proposal
>
> **Product baseline:** GitHub `CodesbyRohit/MediVault`, `origin/main` @ `d80c1bf` (merge of PR #1, 2026-10-12).
> **Status:** This is a provisional structure for the Entrepreneurship Development (ED) mini-project by Rohit Srivastava. It is not the official RGIT template. Source IDs (S#) refer to [`06-source-register.md`](06-source-register.md). All sources were accessed on 2026-10-12.

## 1. Summary

MediVault has real competitors. Some are free government services and some are well-funded private apps.

**Direct competitors** let a person keep their own health records:

- the government's ABHA app;
- DigiLocker's health locker;
- Eka Care;
- Apollo 24|7's personal health records.

**Indirect alternatives** are other ways people already keep reports:

- lab portals;
- general cloud storage;
- the habits of using WhatsApp, the phone gallery or a paper folder.

MediVault's differentiation is **thin and easy to copy**. This analysis makes no claim that MediVault is unique, first, or without competitors.

## 2. Classification

| Alternative | Type | Classification | Why |
|---|---|---|---|
| ABHA app (National Health Authority) | Government personal health record (PHR) app | **Direct** | A citizen keeps, links and shares their own health records [S6, S7] |
| DigiLocker health locker | Government document locker | **Direct** | Stores and links health documents [S8] |
| Eka Care | Private PHR app | **Direct** | Records, ABHA generation, family profiles; paid in-app Eka Pass (per its App Store listing) [S9] |
| Apollo 24\|7 PHR | Provider-ecosystem PHR | **Direct, limited scope** | Records come from the Apollo network [S10] |
| Dr Lal PathLabs, Thyrocare | Lab portals and apps | **Indirect** | Deliver that lab's own reports; not a personal archive across providers [S11, S12] |
| Google One, iCloud+ | General cloud storage | **Indirect** | Store any file, with no medical structure [S13, S14] |
| WhatsApp, phone gallery, paper folder | Manual habits | **Indirect** | Probably the real incumbent [A]; not quantified |

## 3. Verified features and prices

**Key:** ✓ = feature documented by the source · ✗ = documented as absent · ? = not found or unverified

| Feature | MediVault (`d80c1bf`) | ABHA app | DigiLocker health | Eka Care | Apollo 24\|7 PHR | Lab apps | Google One / iCloud+ |
|---|---|---|---|---|---|---|---|
| Upload own PDFs or records | ✓ [REPO] | ✓ to health lockers [S6, V-sec; attribution uncertain] | ✓ [S8, V-sec, 2022] | ✓ store records and reports [S9, V] | ? (the page describes syncing from Apollo, not uploads) [S10, V] | ✗ (lab's own reports only) [A] | ✓ any file [S13, S14] |
| Structured entries (type, date, provider, notes) | ✓ [REPO] | ? | ? | ? | ? | ? | ✗ (folders only) [A] |
| Search and filter own records | ✓ [REPO] | ? | ? | ? | ? | ? | ✓ filename search [A] |
| Records pulled automatically from facilities | ✗ [REPO] | ✓ ABDM facilities [S6, V-sec] | ✓ ABDM facilities [S8, V-sec] | ? (ABHA generation is listed [S9, V]; automatic sync is not confirmed) | ✓ Apollo network [S10, V] | ✓ own lab's reports online [S11, S12, V] | ✗ |
| Family or dependant profiles | ✗ [REPO] | ? | ? | ✓ "profiles for yourself and your family" (App Store listing) [S9, V]; not mentioned on the website [S28, V] | ? | ? | Family sharing ? |
| Consent-based sharing | ✗ [REPO] | ✓ with revoke [S6, V-sec] | ✓ with ABDM professionals [S8, V-sec] | ✓ share with providers [S9, V] | ✓ access control [S10, V] | ? | ✓ link sharing [A] |
| Needs an ABHA address or provider account | ✗ (name, email, password) [REPO] | ✓ ABHA address (mobile, email or ABHA number) [S6, V-sec] | DigiLocker account [A] | ? | Apollo account [A] | Lab ID [S11, V-sec] | Google / Apple ID |
| Access from a phone or the internet | ✗ (local computer only, `127.0.0.1`) [REPO] | ✓ app | ✓ app | ✓ app | ✓ app | ✓ app | ✓ |
| Encryption | New PDFs encrypted at rest; server holds the key; **not end-to-end**; database not encrypted [REPO, S3] | Not researched | Not researched | Not researched | Not researched | Not researched | Not researched |
| Consumer price | Free (prototype; no paid tier exists) [REPO] | Free government service [A] | Free [A] | Free app with an in-app **Eka Pass** subscription: $2.49/month or $19.99/year on the US-dollar App Store [S9, V]; **INR price unverified**; no price on the website [S28, V] | **Unverified** | Apps listed as free [S11, S12, V]; tests are paid [A] | Google: 15 GB free; ₹59 / 30 GB; ₹130 / 100 GB per month [S13, V]. iCloud: 5 GB free; ₹75 / 50 GB; ₹219 / 200 GB per month [S14, V] |

**Caveat:** competitor features come mostly from official PDFs seen in search, app-store listings and marketing pages. They are not hands-on tests. Before the final submission, each "?" and each [V-sec] cell should be checked against the current app. Not researched yet: Practo, Tata 1mg, PharmEasy, DRiefcase, Bajaj Health, and how Metropolis delivers reports.

## 4. Why a customer might choose MediVault, or not

| Existing habit or tool | What it already does well | Where MediVault *might* help (all [P]) |
|---|---|---|
| Phone gallery, WhatsApp | Zero effort; always on the phone | Reports kept with their date, type and provider, findable by search. But MediVault is **not on the phone** today [REPO]. |
| Google Drive, iCloud | Free tier; available everywhere; trusted brands | Medical structure rather than plain folders; no ads (a [PROP] policy) |
| Paper folder | Works offline; familiar to doctors | Searchable; cannot be lost physically (only if backups exist) |
| Hospital or lab portal | Reports arrive automatically | One history across all providers (only if the user uploads each report) |
| ABHA app, DigiLocker, Eka Care | Free or freemium (Eka sells an in-app Eka Pass [S9]); linked to facilities; consent-based sharing; Eka offers family profiles [S9] | No ABHA address needed and independent of providers. **Whether users value this is unknown** [P]. |

## 5. Differentiation strategy (honest)

**What `d80c1bf` actually offers** [REPO, S2, S3]:

- Structured records you can create, edit, delete, search and filter, each with PDFs attached.
- Owner-only access to records and documents. Administrators cannot open record content (as documented and covered by the author's tests).
- A 100 MB document quota per patient.
- New PDFs encrypted at rest, with a key held on the server.

**Limits of that differentiation:**

- **Not unique:** ABHA, Eka Care and the lab apps also organise records [S6, S9, S11].
- **Weaker than competitors on distribution:** MediVault does not receive records from facilities automatically and has no mobile app.
- **Not a security differentiator:** MediVault is not end-to-end encrypted and its database is not encrypted [S3]. Competitors' security was not researched, so no comparison is made.

**Defensible position** [PROP]: a *focused, ad-free, provider-independent personal organiser for people who repeatedly receive diagnostic reports*. Whether anyone values that is **[P]**. Customer discovery tests it (see `03-customer-discovery-toolkit.md`).

**What prevents a larger competitor from copying it?** Nothing, today. The features are easy to copy. Any lasting advantage would have to come from a clearly validated niche, very good usability for that niche, and trust earned over time. All three are untested [P].

## 6. SWOT

| | |
|---|---|
| **Strengths** [REPO] | Owner-only record and document access, as documented. Structured search and filters. New PDFs encrypted at rest. A large automated test suite (author-reported: 496 of 516 passed, 20 skipped [S2]). Near-zero cash cost as a prototype. |
| **Weaknesses** [REPO] | Runs on one computer only, with no HTTPS. No rate limiting or password reset. Database not encrypted. No family profiles, sharing or export. If the keyset is lost, encrypted PDFs are permanently unreadable. Solo student founder. No customer validation. |
| **Opportunities** [V-sec / A] | Some studies report low ABHA awareness [S26, S27], which shows an awareness gap but not demand. An ageing population [S24] gives context, not demand. A Family plan is a hypothesis [A]. |
| **Threats** [V / V-sec / A] | Free and established alternatives [S6, S8, S13, S14]. Well-funded private apps [S9, S10]. Core DPDP obligations reportedly commence on 13 May 2027 [S20, V-sec; verify]. A trust barrier for a student-built service [A]. Easy to copy [A]. |

## 7. Porter's Five Forces (short)

| Force | Assessment | Basis |
|---|---|---|
| Rivalry among existing competitors | **High** | Several free or funded direct competitors [S6, S8, S9, S10] |
| Threat of substitutes | **Very high** | Gallery, WhatsApp, paper, cloud drives cost ₹0 to start [S13, S14; A] |
| Threat of new entrants | **High** for a basic organiser (low barriers). Higher for ABDM-integrated apps (certification needed) [S23, low confidence] | [A] |
| Buyer power | **High**: switching costs are low and free options exist | [A] |
| Supplier power | **Low to moderate**: cloud hosting is a commodity; the payment gateway charges a published fee | [S15, S16, S17] |
