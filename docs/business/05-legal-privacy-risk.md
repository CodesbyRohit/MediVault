# MediVault: Legal, Privacy and Business Risks

> **Labels:** **[REPO]** documented for `origin/main` @ `d80c1bf` (README and PR #1; this code was not run or independently tested in this work) · **[V]** official source, fetched and read · **[V-sec]** secondary source, or an official source seen only in search results · **[A]** assumption · **[P]** pending customer validation · **[PROP]** proposal
>
> **Product baseline:** GitHub `CodesbyRohit/MediVault`, `origin/main` @ `d80c1bf` (merge of PR #1, 2026-10-12).
> **Status:** This is a provisional structure for the Entrepreneurship Development (ED) mini-project by Rohit Srivastava. It is not the official RGIT template. Source IDs (S#) refer to [`06-source-register.md`](06-source-register.md).

> **This is not legal advice.**
> - MediVault makes **no claim** of being legally compliant, certified or ABDM-integrated.
> - Every legal item below is **awaiting primary-source verification**. Most of it comes from secondary summaries, because the official PIB and Gazette pages could not be read in this research.
> - A real commercial launch would need review by a qualified lawyer.

## 1. Security posture at `d80c1bf`, as documented [REPO, S2, S3]

| Area | Documented state |
|---|---|
| Access control | Records and documents are owner-only. Another patient gets 404; an administrator gets 403 and cannot see medical content. The author's tests cover this; it has not been independently tested. |
| Passwords | Stored as BCrypt hashes. CSRF protection is on. |
| PDF encryption | New PDFs are encrypted at rest with Google Tink AES-256-GCM (streaming). The **server holds** the keyset file. |
| What is *not* encrypted | **No end-to-end encryption.** The **database is not encrypted**: record titles, providers and notes are stored as plain data. PDFs uploaded before encryption was added stay in plaintext until a conversion step that is not yet built. |
| If the keyset is lost | **Every encrypted PDF becomes permanently unreadable.** MediVault has no recovery mechanism, by design. |
| Deployment | One computer, listening on `127.0.0.1` only. No HTTPS, rate limiting, account lockout, password reset or email verification. |
| Other gaps | No export, no account deletion, no audit trail, no sharing |

Encryption at rest alone does **not** make MediVault production-ready or legally compliant.

## 2. Student prototype vs a hypothetical commercial service

| Topic | Student prototype (today) | Commercial service (future, hypothetical) |
|---|---|---|
| Data held | **Fictional data only** [REPO]. No real health data. | Real personal and health data belonging to customers |
| Personal data actually handled | Contact details of interview participants in the discovery study | All customer accounts and documents |
| DPDP Act 2023 and DPDP Rules 2025 | How it applies to a student study is **unclear [A]**. Minimise and protect participants' data anyway (see `03-customer-discovery-toolkit.md` §12). | MediVault would likely be a *data fiduciary* (the party that decides how personal data is processed) [A]. Core duties commence **13 May 2027** [S20, V-sec]. Verify. |
| Consent and notice | Spoken consent script for interviews | Itemised notice, consent records, a way to withdraw consent [A; Rules text to verify] |
| Access, correction, erasure, grievance | Delete notes on request | Rights for data principals (the people the data is about), with response processes [A; verify] |
| Security safeguards | Local machine; fictional data | Reasonable safeguards. Rule 6 reportedly requires keeping logs at least 1 year [S20, V-sec]. |
| Breach response | Not applicable | Reportedly: notify affected people and the Data Protection Board "without delay", then send the Board a detailed report within 72 hours [S20, V-sec; verify the Rule 7 text] |
| Cyber incident reporting | Not applicable | CERT-In Directions (2022): report within 6 hours and keep logs for 180 days in India [S22, V-sec]. Applicability to be verified. |
| Retention and erasure | Delete study contacts after the project | Third Schedule erasure periods and a 48-hour notice may apply to certain classes [S20, V-sec]. Verify. |
| Startups | n/a | PIB describes a "simplified compliance framework for start-ups" [S19, V-sec]. The details were **not obtained**. |
| ABDM / ABHA integration | **Not integrated.** No claim made. | Would need the ABDM sandbox, functional milestones and a security audit (WASA) by a CERT-In-empanelled auditor [S23, low confidence]. Verify with NHA. |
| Older IT Act rules | n/a | Status of IT Act s.43A and the SPDI Rules 2011 during the DPDP transition: **not researched** |
| Terms of service and privacy notice | Not needed (no real users) | Drafted and reviewed by a lawyer (cost is [A] in `04-financial-model.md`) |
| Payments and tax | None | GST threshold and registration [S32, V-sec]; consumer-protection rules for subscriptions (not researched) |

## 3. Primary-source verification list (must be done before final submission)

1. **DPDP Rules, 2025:** read the Gazette or MeitY text (third-party summaries give the number as G.S.R. 846(E); **unverified**) and its corrigendum. Check:
   - commencement;
   - Rules 3–8 and 22–23;
   - the Third Schedule;
   - the startup provisions (S18–S20).
2. DPDP Act, 2023: data fiduciary duties, rights of data principals, and whether health data is treated specially.
3. CERT-In Directions of 28 April 2022, read on cert-in.org.in (S22).
4. ABDM: sandbox and certification requirements, and the final Health Data Management Policy (S23).
5. IT Act s.43A and the SPDI Rules, 2011: status during the transition.
6. GST: registration threshold and treatment of an online subscription service (S32).

## 4. Backup, recovery and incident readiness [PROP]

| Area | Proposed practice |
|---|---|
| Keyset | Store the keyset file outside the data folder, as the README requires [S3]. Keep two offline copies in separate places, and test that they can be restored. **Never** keep it with the data backups. |
| Data backup | Back up the database and the records folder together, with the app stopped [S3, local mode]. A commercial service needs automated, tested backups and an off-site copy [A]. |
| Restore drill | Before any real data is stored, rehearse restoring both the database and the PDFs |
| Incident response | Write a one-page plan: detect, contain, assess, notify (deadlines to be verified), review |
| Account deletion and export | Not implemented at `d80c1bf` [REPO]. Required before real users [PROP]. |

## 5. Risk register

**Likelihood (L) and impact (I) scale:** H = high, M = medium, L = low.

| # | Risk | L | I | Mitigation | Owner |
|---|---|---|---|---|---|
| R1 | No real demand: neither segment shows a retrieval problem | M | H | 20 exploratory interviews with decision rules set in advance; pivot or reframe if both segments fail | Founder |
| R2 | Users stay with free alternatives (ABHA, Drive, gallery) | H | H | Test the niche positioning; do not compete on storage price | Founder |
| R3 | Nobody will pay for a Family plan | H | H | Ask about past spending only; keep the model labelled unvalidated | Founder |
| R4 | Keyset lost, so encrypted PDFs are permanently unreadable | L–M | H | Two tested offline keyset copies; restore drill; document the procedure | Founder |
| R5 | Real medical data entered into the prototype | M | H | "Fictional data only" banner [REPO]; fictional PDFs supplied in sessions; a consent script that forbids real documents | Founder |
| R6 | Data breach in a future hosted service | M | H | Hardening (HTTPS, rate limiting, password reset), an independent security test, an incident plan, verified breach-notice duties | Founder (future) |
| R7 | Misreading legal obligations | M | H | Primary-source verification list (§3); lawyer review before launch | Founder plus legal adviser (future) |
| R8 | Trust barrier for a student-built service | H | M | Plain-language security statement, including the limits; no overclaiming | Founder |
| R9 | Competitor copies the features | H | M | Focus on a validated niche and usability; accept that the moat is weak | Founder |
| R10 | Founder time limits (about 727 h in the year-one scenario) | H | M | Keep scope small; phase the work; treat the time cost as real in the financial model | Founder |
| R11 | Vendor price, tax or exchange-rate changes | M | L | Sensitivity analysis; refresh prices and the RBI rate before submission | Founder |
| R12 | Study participants' personal data mishandled | L | M | Minimal data, codes instead of names, separate storage, deletion at project end | Founder |
| R13 | Scale assumptions break (F assumed constant) | M | M | Re-model infrastructure if users exceed about 2,400 | Founder |
