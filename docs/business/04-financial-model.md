# MediVault: Financial Feasibility Model (Illustrative)

> **Labels:** **[REPO]** documented for `origin/main` @ `d80c1bf` (README and PR #1; this code was not run or independently tested in this work) · **[V]** official source, fetched and read · **[V-sec]** secondary source, or an official source seen only in search results · **[A]** assumption · **[P]** pending customer validation · **[PROP]** proposal
>
> **Product baseline:** GitHub `CodesbyRohit/MediVault`, `origin/main` @ `d80c1bf` (merge of PR #1, 2026-10-12).
> **Status:** This is a provisional structure for the Entrepreneurship Development (ED) mini-project by Rohit Srivastava. It is not the official RGIT template. Source IDs (S#) refer to [`06-source-register.md`](06-source-register.md).

**Read this first.** Everything below is **illustrative scenario arithmetic, not a forecast**.

- **Assumptions with no evidence behind them:** price, conversion, acquisition, support time and storage per user are founder assumptions [A].
- **Still unknown:** no customer has been interviewed and there is no evidence of willingness to pay [P].
- **Prices awaiting confirmation:** vendor prices are quoted from official pages where marked [V]. Taxes, legal fees and security-testing costs are **provisional** until verified (see section 9).

## 1. Conventions

| Item | Value | Label |
|---|---|---|
| Currency | INR, per month unless stated | |
| Planning exchange rate (X) | ₹96 per USD | [A], anchored to the RBI reference rate of 96.2948 on 18 May 2026. The latest rate seen in Stage 7 was 96.6149 on 09 Oct 2026, about 0.6% higher, which is well inside the ±10% sensitivity [S29, V-sec]. **Refresh from RBI before submission.** |
| GST on DigitalOcean bills | 18% added | [A]; whether DigitalOcean charges GST to Indian buyers is **unverified** |
| GST on MediVault's own sales | Not charged in the base case, assuming turnover stays below the ₹20 lakh services threshold | Threshold [S32, V-sec]; treatment **provisional** |
| Payment fee (g) | 2% platform fee + 18% GST on the fee = 2.36% of the transaction | [S15, V] |

## 2. Student prototype (what exists today)

At `d80c1bf` the app runs on one computer, listening on `127.0.0.1` only, and must hold **fictional data only** [REPO, S3]. It earns no revenue and has no hosting cost.

| Item | Low | Base | High | Label |
|---|---|---|---|---|
| Hosting and storage | ₹0 | ₹0 | ₹0 | [REPO] runs locally |
| Encryption keyset + offline copy on the student's own USB stick | ₹0 | ₹0 | ₹0 | [A] |
| Interview travel and refreshments | ₹0 | ₹600 | ₹1,500 | [A] |
| Printed consent forms | ₹0 | ₹200 | ₹300 | [A] |
| **Cash per semester** | **₹0** | **₹800** | **₹1,800** | |
| Founder time for discovery and analysis | 40 h | 60 h | 100 h | [A] |
| Economic value of that time at ₹200/h | ₹8,000 | ₹12,000 | ₹20,000 | [A], non-cash |

Development time already spent is a sunk cost and is not counted. Founder time is still given a value, because a real business cannot treat it as free.

## 3. Hypothetical commercial service: parameters

| Symbol | Meaning | Base value | Label |
|---|---|---|---|
| P | Family plan price | ₹99/month | [A] pricing hypothesis, not willingness to pay |
| g·P | Payment fee | 0.0236 × 99 = ₹2.3364 | [S15, V] |
| r | Storage cost beyond the allowance = $0.02 × 1.18 × 96 | ₹2.2656 per GiB-month | [S17, V] × [A] |
| A | Storage included in the $5 Spaces plan | 250 GiB | [S17, V] |
| sₚ | Storage per family: 0.2 GiB doubled for a backup copy in the same account | 0.4 GiB | [A] |
| s_f | Storage per free user: 0.02 GiB doubled for backup | 0.04 GiB | [A] |
| hₚ | Support per paying family: 0.1 h/month × ₹300/h | ₹30 | [A] |
| h_f | Support per free user: 1/300 h/month × ₹300/h | ₹1 | [A] |
| c | Free-to-paid conversion | 2% / **5%** / 10% | [A] illustrative, no source |

A true off-site backup at a second provider would cost more. That cost is **not modelled** and is provisional.

## 4. Costs, each counted once

### 4a. Hosting (quoted USD prices, converted)

| Item | Lean | Base | Robust | Source |
|---|---|---|---|---|
| App server (Droplet) | $6 (1 GiB) | $12 (2 GiB) | $48 (2 × 4 GiB) | [S16, V]. Whether 1 GiB is enough is [A]. |
| Server backups | weekly 20% = $1.20 | daily 30% = $3.60 | daily 30% = $14.40 | [S16, V] |
| Managed MySQL | $15.15 | $15.15 | $30.30 | Single node [S30, V]; the robust figure assumes double the price for a standby node [A, standby price unverified] |
| Object storage (250 GiB included) | $5 | $5 | $5 | [S17, V] |
| **USD subtotal** | **$27.35** | **$35.75** | **$97.70** | |
| **× 1.18 × ₹96** | **₹3,098.21** | **₹4,049.76 ≈ ₹4,050** | **₹11,067.46** | Formula: USD × 1.18 × 96 |

### 4b. Classification of costs, base case

| Category | Item | Per month | Per year |
|---|---|---|---|
| **Cash, recurring (K)** | Hosting (4a) | ₹4,050 | ₹48,600 |
| | Domain and DNS ₹150 [A; `.in` domain prices in S31]; keyset backup media and password manager ₹300 [A]; monitoring and log retention ₹500 [A]; transactional email ₹300 [A] | ₹1,250 | ₹15,000 |
| | **K** | **₹5,300** | **₹63,600** |
| **Cash, annual (paid in advance)** | Independent security test [A, no quote obtained] | ₹5,000 accrued | ₹60,000 |
| **Cash, one-time at launch** | Lawyer review of terms and privacy notice ₹30,000 [A]; hardware security keys for admin ₹5,000 [A] | | ₹35,000 |
| **Cash, variable** | Payment fee per paying family | ₹2.3364 | |
| | Storage beyond 250 GiB | r × max(0, total GiB − 250) | |
| **Economic (founder time, unpaid in year one)** | Fixed operations, maintenance and compliance O = 40 h × ₹300 | ₹12,000 | ₹1,44,000 |
| | Support time | hₚ per family, h_f per free user | |
| | Hardening before launch: 120 h × ₹300 (HTTPS hosting, rate limiting, password reset, export, account deletion; these features are not in `d80c1bf` [REPO]) | | ₹36,000 one-time |

**Steady-state fixed cost:** F = K + security accrual + O = 5,300 + 5,000 + 12,000 = **₹22,300/month**.

**Rounding:** K uses hosting rounded to ₹4,050. With the unrounded ₹4,049.76, K = ₹5,299.76 and F = ₹22,299.76. That leaves every break-even figure unchanged and shifts the year-one totals by less than ₹3 (section 7).

### 4c. Lean and robust variants (all [A])

| Item | Lean | Base | Robust |
|---|---|---|---|
| Hosting | ₹3,098 | ₹4,050 | ₹11,067 |
| Domain / keyset backup / monitoring / email | ₹100 / ₹100 / ₹0 / ₹0 | ₹150 / ₹300 / ₹500 / ₹300 | ₹200 / ₹1,000 / ₹2,000 / ₹800 |
| Security test (annual, shown per month) | ₹2,083 (₹25,000 a year) | ₹5,000 (₹60,000 a year) | ₹12,500 (₹1,50,000 a year) |
| Founder fixed time | 20 h × ₹200 = ₹4,000 | 40 h × ₹300 = ₹12,000 | 80 h × ₹400 = ₹32,000 |
| **F** | **₹9,382** | **₹22,300** | **₹59,567** |
| Launch cash (legal + keys) | ₹15,000 + ₹4,000 | ₹30,000 + ₹5,000 | ₹75,000 + ₹8,000 |
| Hardening time | 80 h × ₹200 | 120 h × ₹300 | 200 h × ₹400 |
| Support per paying family | ₹15 | ₹30 | ₹60 |

**Not included (not researched):** company incorporation, cyber insurance, an incident-response retainer, paid marketing (₹0 is assumed), income tax, chartered-accountant fees and a second-provider backup.

In the base case, hosting is about **18%** of F. Founder time and security testing together are about **76%**. The service mostly costs people and security, not servers.

## 5. Break-even

### 5a. Paying-only break-even

This assumes every user pays and there are **no free users**.

- **Formula 1, contribution per family:** Cₚ = P − g·P − hₚ = 99 − 2.3364 − 30 = **₹66.6636**
- **Formula 2, families needed:** N = F ÷ Cₚ = 22,300 ÷ 66.6636 = 334.5 → **335**
- **Storage check:** 335 × 0.4 = 134 GiB, which is within the 250 GiB allowance, so storage costs nothing extra.

| Cost scenario | Cₚ | Paying families needed |
|---|---|---|
| Lean (support ₹15) | ₹81.66 | **115** |
| Base | ₹66.66 | **335** |
| Robust (support ₹60) | ₹36.66 | **1,651** (storage goes over the allowance, so the overage formula is used) |

**Fully-loaded storage variant.** This is a conservative variant that ignores the 250 GiB allowance and charges each user's storage at r. Cₚ = 66.6636 − 0.906 = 65.758, so N = **340**.

### 5b. Break-even including free users

At conversion c, every paying family comes with (1 − c)/c free users. At 5% that is 19 free users per family.

- **Contribution per family before storage:** Cₚ − 19 × h_f = 66.6636 − 19 = ₹47.6636
- **Storage per family, including its free users:** 0.4 + 19 × 0.04 = 1.16 GiB
- **Profit once storage exceeds the allowance:** profit = 47.6636·p − r·(1.16·p − 250) − F
- **Set profit = 0:** 45.0355·p + 566.4 = 22,300, so p = 482.6 → **483 paying families**, or **9,660 users in total**. Storage is 560 GiB, which is over the allowance, so the overage formula applies.

| Conversion (illustrative) | Free users per family | Paying families needed | Total users | Storage |
|---|---|---|---|---|
| 2% | 49 | **1,765** | 88,250 | 4,165 GiB |
| 5% | 19 | **483** | 9,660 | 560 GiB |
| 10% | 9 | **389** | 3,890 | 296 GiB |

Fully-loaded variant at 5%: 22,300 ÷ 45.0355 = 495.2 → **496**.

### 5c. Why 335 and 483 differ

Free users cost money (support time and storage) but bring no revenue. Each paying family has to cover its 19 free users. That drops its contribution from ₹66.66 to about ₹45.04, so more paying families are needed.

Two warnings follow:

- **At 2% conversion,** if each free user costs ₹3 in support, the contribution per family becomes negative (66.66 − 147). The service would **never break even**.
- **At large user counts,** F is assumed constant, which only holds at small scale. The 2% row (about 88,000 users) would need much larger infrastructure and much more support than modelled, so its break-even is **understated**.

## 6. Sensitivity

Base case, paying-only break-even, using the allowance model. Each row changes **one factor at a time**.

| Factor | Low | Base | High | Note |
|---|---|---|---|---|
| Price ₹49 / ₹99 / ₹149 [A] | **1,284** | 335 | **194** | At ₹49, storage passes 250 GiB and the overage formula applies |
| Support per family ₹15 / ₹30 / ₹60 [A] | **274** | 335 | **609** | At ₹60, storage is 244 GiB, just inside the allowance |
| Founder fixed time ₹0 / ₹12,000 / ₹32,000 [A] | **155** | 335 | **635** | The support rate stays at ₹300/h |
| Storage per family 0.2 / 1 / 5 GiB (× 2 for backup) [A] | 335 | **350** | **494** | Over the allowance at 1 GiB and 5 GiB |
| Exchange rate −10% / +10% [A] | **329** | 335 | **341** | Small effect |
| GST-registered (₹99 including GST, so net revenue ₹83.90) | | **433** | | Input tax credit ignored, which is conservative. Treatment **provisional**. |
| Conversion 2% / 5% / 10%, including free users [A] | **1,765** | 483 | **389** | From section 5b |
| Fully-loaded storage variant | | 340 / 496 | | Paying-only / including free users at 5% |

**Biggest drivers:** price and willingness to pay, support time, founder time and conversion. Storage only matters once it passes the allowance, and the exchange rate barely matters.

**GST threshold:** ₹20 lakh a year is about 1,684 paying families at ₹99. That is well above base-case break-even, so the service would likely stay unregistered at that scale [A]. Registering would allow input tax credit on GST paid to suppliers. That is **not modelled**.

## 7. Year-one scenario (not a forecast)

**Scenario assumptions [A]:**

- **Timing:** hardening is done locally in month 0. Hosting starts in month 1.
- **Growth:** paying families pₘ = 10 × m, so 10 to 120 over the year. This assumes **acquisition continues every month, nobody churns and there is no paid marketing**.
- **Mix:** conversion is 5%, so there are 19 free users per paying family.
- **Pay:** the founder receives no cash. Founder time appears only as an economic cost.
- **Fixed costs:** assumed constant up to 2,400 users, which is this scenario's month-12 total [A]. That is not a tested capacity limit.
- **Storage:** within the allowance all year. Month 12: 120 × 0.4 + 2,280 × 0.04 = 139 GiB.

### A. Operating cash flow (recurring)

**Formula:** operating cash in month m = (P − g·P) × pₘ − K = **96.6636 × pₘ − 5,300**

| Month | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Paying families | 10 | 20 | 30 | 40 | 50 | 60 | 70 | 80 | 90 | 100 | 110 | 120 |
| Revenue (₹) | 990 | 1,980 | 2,970 | 3,960 | 4,950 | 5,940 | 6,930 | 7,920 | 8,910 | 9,900 | 10,890 | 11,880 |
| Operating cash (₹) | −4,333 | −3,367 | −2,400 | −1,433 | −467 | +500 | +1,466 | +2,433 | +3,400 | +4,366 | +5,333 | +6,300 |
| Cumulative, including month 0 (₹) | −99,333 | −1,02,700 | −1,05,100 | −1,06,534 | **−1,07,000** | −1,06,501 | −1,05,034 | −1,02,601 | −99,201 | −94,835 | −89,502 | **−83,202** |

- **Break-even month:** operating cash turns positive once pₘ is above 5,300 ÷ 96.6636 = 54.8, so in **month 6**.
- **Year total:** revenue ₹77,220 − payment fees ₹1,822 − recurring costs ₹63,600 = **+₹11,798**.

### B. Month-0 cash spending

Launch costs ₹35,000 + first annual security test ₹60,000 = **₹95,000**.

### C. Economic (non-cash) costs: founder time

| Item | Formula | Hours | ₹ |
|---|---|---|---|
| Fixed operations | 40 h × 12 | 480 | 1,44,000 |
| Support for paying families | 0.1 h × 780 family-months | 78 | 23,400 |
| Support for free users | 14,820 free-user-months ÷ 300 | 49.4 | 14,820 |
| Hardening before launch | 120 h | 120 | 36,000 |
| **Total** | | **≈ 727 h (≈ 14 h/week)** | **2,18,220** |

### D. Year-one totals and reconciliation

| View | Calculation | Result |
|---|---|---|
| Operating cash | A | **+₹11,798** |
| Total cash | A − B = 11,798 − 95,000 | **−₹83,202** (lowest point −₹1,07,000 in month 5) |
| Economic | Total cash − C = −83,202 − 2,18,220 | **−₹3,01,422** |

**Cross-check, so that no cost is counted twice:**

1. Twelve months of F = 12 × ₹22,300 = ₹2,67,600. That is recurring cash ₹63,600 + security ₹60,000 + founder fixed time ₹1,44,000.
2. Add launch cash ₹35,000, hardening ₹36,000, fees ₹1,822 and support ₹38,220. Total cost = **₹3,78,642**.
3. Revenue is ₹77,220, so the result is **−₹3,01,422**, the same as in D.

**Rounding note:** with unrounded hosting (₹4,049.76), the totals are +₹11,800 / −₹83,200 / −₹3,01,420. The difference is under ₹3.

**Scenario arithmetic, not a forecast:** at +10 families a month with no churn, paying-only break-even (335) would come in **about month 34**. Break-even including free users (483) would come in **about month 49**.

## 8. Preliminary conclusions

1. **Prototype:** feasible at almost no cash cost (₹0–₹1,800 a semester) plus 40–100 founder hours [A].
2. **Commercial service:** viability is **unproven**. In the base case it needs about **335** paying families (paying-only) or about **483** (including free users at an illustrative 5% conversion). There is **no evidence yet** that anyone would pay [P].
3. **Cost structure:** costs are mostly people and security, not servers.
4. **Most uncertain inputs:** price and willingness to pay, support time and conversion. These are the questions the customer-discovery study in [`03-customer-discovery-toolkit.md`](03-customer-discovery-toolkit.md) starts to explore, by asking about **past spending**, not hypothetical "would you pay?" questions.
5. **Operational risk beyond money:** if the server-held keyset is lost, encrypted PDFs become permanently unreadable [REPO, S3]. Keyset backup is a necessary recurring cost and procedure.

## 9. Provisional items (not established facts)

- DigitalOcean: GST charged to Indian buyers; whether Spaces and managed databases are offered in the Bangalore datacenter (S16 confirms the datacenter, for Droplets); managed-database standby price and backup retention (S16, S17, S30)
- GST threshold, registration treatment and input tax credit (S32)
- Exchange rate, which must be refreshed from RBI (S29)
- Legal review fees, security-testing quotes from CERT-In-empanelled auditors, and monitoring and email costs (no quotes; all [A])
- Willingness to pay, conversion, acquisition rate, churn, support time and storage per user (all [A] or [P])
- Legal obligations that would affect costs (DPDP, CERT-In, ABDM): see [`05-legal-privacy-risk.md`](05-legal-privacy-risk.md)
