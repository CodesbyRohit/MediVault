# MediVault: Research Source Register

> **Labels:** **[REPO]** documented for `origin/main` @ `d80c1bf` (README and PR #1; this code was not run or independently tested in this work) · **[V]** official source, fetched and read · **[V-sec]** secondary source, or an official source seen only in search results · **[A]** assumption · **[P]** pending customer validation · **[PROP]** proposal
>
> **Product baseline:** GitHub `CodesbyRohit/MediVault`, `origin/main` @ `d80c1bf` (merge of PR #1, 2026-10-12).
> **Status:** This is a provisional structure for the Entrepreneurship Development (ED) mini-project by Rohit Srivastava. It is not the official RGIT template.

## How to read this register

- **Access date:** every source was accessed on **2026-10-12**, first during research (Stage 3) and again during the final audit (Stage 7). "Re-check" notes record what the Stage 7 attempt returned.
- **Fetch status** says how the claim was obtained:
  - **Fetched**: the page was retrieved and read.
  - **Fetched, unreadable**: the file was retrieved, but its text could not be extracted.
  - **Search only**: the claim comes from search-result summaries; the page itself was not read.
  - **Blocked**: the site refused the request (HTTP 403, refused connection, certificate error or connection reset).
- **Verification status:**
  - **Verified**: an official source, or the publisher's own page or listing, fetched and read.
  - **Secondary**: a law firm, news site, aggregator or vendor blog, or an official source seen only in search.
  - **Unverified**: cannot be confirmed yet.
  - **Verify before submission**: must be checked against a primary source before the final ED submission.
- **No other evidence:** no claim in the ED package may rely on a source that is not listed here.

## Register

| ID | Publisher | URL | Date / status of source | Claim supported | Fetch status | Verification |
|---|---|---|---|---|---|---|
| S1 | GitHub (REST API) | https://api.github.com/repos/CodesbyRohit/MediVault/commits?sha=main | Live; re-checked: `main` still at `d80c1bf` | `origin/main` is `d80c1bf` (merge of PR #1), two commits after `7eefd62` | Fetched | Verified |
| S2 | GitHub | https://github.com/CodesbyRohit/MediVault/pull/1 | Merged 2026-10-12 15:41 UTC | M3 record management; document deletion; 100 MB per-patient quota; Tink AES-256-GCM encryption of new PDFs at rest. Test run reported by the author: 516 tests, 496 passed, 0 failed, 20 skipped. | Fetched (via API) | Verified as the author's own description; code not run in this work |
| S3 | GitHub (repository README) | https://raw.githubusercontent.com/CodesbyRohit/MediVault/main/README.md | `main` @ `d80c1bf` (the README itself names no commit) | Upload checks (`.pdf` extension, declared type, PDF header, `%%EOF`); 10 MB per file and 100 MB per patient; size and SHA-256 checked before download; another patient gets 404, admin gets 403; admins cannot see content; BCrypt and CSRF; `127.0.0.1` only; older PDFs stay plaintext; app refuses to start without a keyset; encryption at rest is not end-to-end; database not encrypted; lost keyset makes encrypted PDFs permanently unreadable; no rate limiting, password reset or email verification; MySQL 8.4 not verified | Fetched (re-checked in Stage 7) | Verified as documentation |
| S4 | Press Information Bureau | https://static.pib.gov.in/WriteReadData/specificdocs/documents/2026/jul/doc202676912801.pdf | July 2026 | Reported: about 93 crore ABHA accounts and about 104 crore linked health records (May 2026) | Fetched, unreadable (figures seen only in a search summary) | **Unverified: do not use as fact** |
| S5 | National Health Authority | https://dashboard.abdm.gov.in/abdm/ | Live dashboard | Current ABHA and linked-record counts | Blocked (connection reset, Stage 3 and Stage 7) | Verify before submission |
| S6 | National Health Authority (ABDM) | https://abdm.gov.in:8081/uploads/ABHA_App_8413f7c295.pdf | Undated | ABHA app: create an ABHA address; link facility records; view lab reports, prescriptions and vaccination certificates; consent-based sharing with revoke. The "self-upload to health lockers" detail came from other search results, so its attribution to S6 is **uncertain**. | Search only (Stage 7 re-check: connection refused) | Secondary |
| S7 | National Health Authority (ABDM) | https://abdm.gov.in:8081/uploads/HIP_HIU_Guidelines_f85df336ec.pdf | Undated | Personal health record (PHR) apps may offer long-term storage, upload of records and sharing on the ABDM network | Search only (Stage 7 re-check: connection refused) | Secondary |
| S8 | National Health Authority (ABDM); Business Today | https://abdm.gov.in:8081/uploads/Press_Release_Page_f254e997c5.pdf ; https://businesstoday.in/latest/trends/story/digilocker-users-can-now-link-health-records-with-ayushman-bharat-352448-2022-11-10 | 10 Nov 2022 | DigiLocker health locker: store health documents; link ABHA records from ABDM-registered facilities; upload old records; share with ABDM-registered professionals | Business Today article fetched; NHA PDF refused the connection | Secondary (news report, 2022); **current status unverified** |
| S9 | Apple App Store (Eka Care listing; developer Orbi Health Private Limited) | https://apps.apple.com/app/id1561621558 | Current listing | "Create and maintain digital health profiles for yourself and your family"; generate ABHA; store records, prescriptions and diagnostic reports; share with providers; track blood sugar over time. Listed as **Free with in-app purchases: "Eka Pass Monthly $2.49", "Eka Pass Yearly $19.99"** (US-dollar storefront; the INR price is unverified). | Fetched | Verified (listing text read directly) |
| S10 | Apollo 24\|7 | https://apollo247.com/guest-health-records | Undated | Apollo PHR will "sync all your Health Records in real time from the entire Apollo ecosystem" (hospitals, diagnostics, clinics); users have "complete control over who can access" their records | Fetched | Verified (publisher's own page) |
| S11 | Dr Lal PathLabs; Apple App Store | https://lalpathlabs.com/download-app ; https://apps.apple.com/app/id1111250276 | Current | App Store listing: "Access and download blood test reports online"; current and past reports in one place; app listed as free. The download-app page mentions lab reports only in general terms. A cumulative report or trends are **not** supported by either page. | Fetched | Verified (listing text) |
| S12 | Apple App Store (Thyrocare listing) | https://apps.apple.com/app/id1117762349 | Current | "Access digital reports anytime"; reports can be checked with a QR code; app listed as free | Fetched | Verified (listing text) |
| S13 | Google | https://one.google.com/about/plans | Live; INR prices shown | 15 GB free; Lite 30 GB ₹59/month; Basic 100 GB ₹130/month; AI Pro 5 TB ₹1,950/month; annual billing "save up to 16%" | Fetched (re-checked) | Verified |
| S14 | Apple Support (India) | https://support.apple.com/en-in/108047 | Published 29 Sep 2026 | iCloud: 5 GB free; iCloud+ 50 GB ₹75, 200 GB ₹219, 2 TB ₹749 per month; taxes included | Fetched (re-checked) | Verified |
| S15 | Razorpay | https://razorpay.com/pricing/ | Live | Standard platform fee 2% (UPI, cards, netbanking); 18% GST on the fee; ₹0 setup and ₹0 annual fee | Fetched (re-checked) | Verified |
| S16 | DigitalOcean | https://www.digitalocean.com/pricing/droplets | Live; USD | Basic Droplets (virtual servers) $4 / $6 / $12 / $24 per month (512 MiB / 1 / 2 / 4 GiB); backups 20% (weekly) or 30% (daily) of the Droplet price. FAQ: "DigitalOcean has a datacenter in Bangalore, India." Taxes: "required by law to apply taxes in some countries" (GST not named). | Fetched (re-checked) | Verified (whether Indian GST is charged remains unverified) |
| S17 | DigitalOcean | https://www.digitalocean.com/pricing/spaces-object-storage | Live; USD | Spaces object storage $5/month including one bucket, 250 GiB storage and 1 TiB outbound transfer; $0.02/GiB extra storage; $0.01/GiB extra transfer | Fetched (re-checked) | Verified (whether the allowance is per account or per bucket, and whether Spaces runs in Bangalore, are not stated) |
| S18 | Press Information Bureau (MeitY) | https://www.pib.gov.in/PressReleasePage.aspx?PRID=2190014 | 14 Nov 2025 (other PIB items say 13 Nov) | DPDP Rules, 2025 notified with an 18-month phased timeline | Blocked (HTTP 403, Stage 3 and Stage 7); claim seen in search | Secondary; verify before submission |
| S19 | Press Information Bureau (MeitY) | https://www.pib.gov.in/PressReleasePage.aspx?PRID=2198217 | 3 Dec 2025 | The DPDP Act and Rules provide a "simplified compliance framework for start-ups and certain data fiduciaries" (details not obtained) | Blocked (HTTP 403); claim seen in search | Secondary; verify before submission |
| S20 | SCC Online (SCC Times, 14 Nov 2025); Mondaq | https://www.scconline.com/blog/post/2025/11/14/meity-notified-digital-personal-data-protection-rules-2025/ ; https://www.mondaq.com/india/data-protection/1708164/digital-personal-data-protection-rules-2025-notified | Nov 2025 | MeitY notified the DPDP Rules on 13 Nov 2025. Rules 1, 2 and 17–21 apply immediately; Rule 4 (consent managers) from 13 Nov 2026; Rules 3, 5–16 and 22–23 from 13 May 2027. Rule 6: keep logs at least one year. Rule 7: notify the Board and affected people "without delay", with a detailed report to the Board within 72 hours. Rule 8: 48 hours' notice before erasure. **Neither article gives the Gazette (G.S.R.) number.** | Fetched (Stage 7) | Secondary (legal commentary); verify against the Gazette before submission |
| S21 | Protiviti | https://www.protiviti.com/sites/default/files/2025-11/flash_compliance_update_dpdp_rules-2025.pdf | Nov 2025 (per search result) | Originally cited for Rules 6, 7 and 8. Those claims are now cited to S20, which was read directly. | Fetched, unreadable (Stage 7) | Not relied on |
| S22 | PSA Legal Counsellors (Arya Tripathy) | https://psalegal.com/new-cert-in-directions-overview-and-implications/ | 5 May 2022 | CERT-In Directions of 28 Apr 2022: report listed cyber incidents within 6 hours; keep logs of all ICT systems for 180 days within India; applies to service providers, intermediaries, data centres, body corporates and government organisations | Fetched (Stage 7) | Secondary; verify the primary Directions on cert-in.org.in before submission |
| S23 | Nirmitee; Astra (vendor blogs) | https://nirmitee.io/blog/abdm-certification-process-sandbox-to-production-guide/ ; https://www.getastra.com/blog/compliance/abha-web-application-security-certificate | Nirmitee published 18 Mar 2026, updated 29 Sep 2026 | ABDM sandbox registration; functional testing of the M1–M3 milestones by an NHA-authorised agency; then a web application security audit (WASA) by a CERT-In-empanelled agency | Nirmitee fetched; Astra blocked (HTTP 403) | Secondary, low confidence; verify with NHA before submission |
| S24 | ThePrint; Factly (reporting MoSPI "Elderly in India 2021") | https://theprint.in/india/indias-elderly-population-to-rise-41-over-next-decade-to-touch-194-mn-in-2031-govt-report/710476/ ; https://factly.in/data-the-share-of-elderly-population-is-set-to-increase-to-13-1-in-2031-which-was-5-6-in-1961 | 2021 | People aged 60+: 10.1% of the population in 2021 (about 138 million), projected 13.1% in 2031 (about 194 million) | Search only (Stage 7 re-check: ThePrint connection reset, Factly HTTP 403; the MoSPI PDF exceeded the fetch size limit) | Secondary; primary not read |
| S25 | International Institute for Population Sciences (LASI Wave 1) | https://iipsindia.ac.in/sites/default/files/LASI_India_Executive_Summary_0.pdf | 2020 | Survey scope only; chronic-condition figures for people aged 60+ were **not** extracted | Fetched, unreadable | Unverified figures |
| S26 | Cureus via PubMed Central | https://pmc.ncbi.nlm.nih.gov/articles/PMC12092953/ | 2025, Cureus 17(4):e82605 | Women's outpatient-department (OPD) study, n=250, Guwahati (Northeast India): use of digital health apps was not significantly related to age, education, socioeconomic status or ABHA awareness. The paper has **several internal inconsistencies** (for example, 125/250 reported as 60%), so it is weak evidence. | Fetched (Stage 7) | Verified (article read); low evidential weight |
| S27 | International Journal of Community Medicine and Public Health 13(7) (Pandey, Sharma, Khalid) | https://www.ijcmph.com/index.php/ijcmph/article/view/15784 | Published 30 Jun 2026 | Tinwari village, Dehradun: 100 of 120 (83.3%) mobile-phone users had zero awareness of ABDM or the ABHA app | Fetched (Stage 7) | Verified (article page read) |
| S28 | Eka Care | https://www.eka.care/ | Live | Website describes "EKA PHR" ("Carry your health history everywhere"), a records analyser and a "Create ABHA" link. No consumer price, "Eka Pass" or family profiles are mentioned on the website (see S9 for the app listing). | Fetched (re-checked) | Verified (website content) |
| S29 | Metropolitan Stock Exchange of India (archive of RBI reference rates) | https://www.msei.in/markets/currency/historical-data/rbireferenceratearchives.aspx | Stage 3 search result: 96.2948 on 18 May 2026. Stage 7 page view: **96.6149 on 09 Oct 2026** | USD/INR RBI reference rate (the planning rate of ₹96 is anchored to it) | Fetched (Stage 7; the 18 May value is no longer shown on the page) | Secondary; **refresh from RBI before submission** |
| S30 | DigitalOcean | https://www.digitalocean.com/pricing/managed-databases | Live; USD | Managed MySQL/PostgreSQL, cheapest single node $15.15/month; standby node price and backup retention not shown | Fetched (re-checked) | Verified (standby price unverified) |
| S31 | domainoffer.net (price tracker) | https://domainoffer.net/tld/in/one | Snapshot 12 Oct 2026 | `.in` domain: from $1.02 first year (GoDaddy, new customers only); one.com $1.99; renewals for the registrars compared $9.58–$17.99/year | Fetched (Stage 7) | Secondary |
| S32 | CalcGuru (tax guide) | https://calcguru.in/?p=3364 | 27 Jul 2026 | GST registration threshold for services ₹20 lakh in normal-category states (Maharashtra is not among the lower-limit states) | Fetched (Stage 7) | Secondary; verify on the GST portal / CBIC before submission |

## Primary documents located but not readable here (not used as sources)

The Stage 7 audit found these, but their text could not be read here. They are **not** cited. Read them manually before submission:

- the CERT-In Directions PDF on cert-in.org.in (file metadata dated 27 Apr 2022);
- a PIB backgrounder PDF, "DPDP Rules, 2025 Notified";
- MoSPI's "Elderly in India 2021" PDF (too large to fetch).

## Items to check against a primary source before final submission

1. **ABHA:** current account and linked-record totals (S4, S5).
2. **DPDP Rules, 2025:** read the Gazette notification. Third-party summaries give the number as G.S.R. 846(E), which is **unverified**. Check:
   - its corrigendum;
   - the commencement schedule;
   - Rules 6, 7 and 8;
   - the startup provisions (S18–S20);
   - whether health data is treated specially under the Act.
3. **CERT-In:** the Directions of 28 Apr 2022, from cert-in.org.in (S22).
4. **ABDM:** integration and certification requirements, and the final Health Data Management Policy (S23).
5. **IT Act:** the status of s.43A and the SPDI Rules, 2011 during the DPDP transition. Not yet researched.
6. **DigiLocker:** whether the health locker still offers the features in S8.
7. **Competitor prices:**
   - Eka Pass in INR (S9 shows only US-dollar storefront prices);
   - Apollo 24|7 and Practo consumer prices (not found).
8. **DigitalOcean:**
   - whether it charges GST to Indian customers;
   - whether Spaces and managed databases are offered in the Bangalore datacenter (S16 confirms the datacenter, for Droplets);
   - managed-database standby price and backup retention (S16, S17, S30).
9. **GST:** the registration threshold and input tax credit (S32).
10. **Exchange rate:** the RBI reference rate from RBI itself on the submission date (S29).
11. **Security testing and legal review:** no quotes have been obtained (VAPT/WASA, legal fees); these remain [A].
12. **Population and health data:** primary MoSPI and LASI figures (S24, S25).
