# MediVault: Customer Discovery Toolkit

> **Labels:** **[REPO]** documented for `origin/main` @ `d80c1bf` (README and PR #1; this code was not run or independently tested in this work) · **[V]** official source, fetched and read · **[V-sec]** secondary source, or an official source seen only in search results · **[A]** assumption · **[P]** pending customer validation · **[PROP]** proposal
>
> **Product baseline:** GitHub `CodesbyRohit/MediVault`, `origin/main` @ `d80c1bf` (merge of PR #1, 2026-10-12).
> **Status:** This is a provisional structure for the Entrepreneurship Development (ED) mini-project by Rohit Srivastava. It is not the official RGIT template.

> **All findings: PENDING VALIDATION.** No interviews have been conducted. This file holds blank templates only. It must never contain made-up responses, quotes, percentages or testimonials.

## 1. Study design

| Item | Design [PROP] |
|---|---|
| Purpose | Exploratory test of two segment hypotheses. **Not statistically representative.** |
| Segments | **H2a:** individuals who had repeated diagnostic tests in the last 12 months (the provisional working hypothesis). **H1:** Mumbai adults who help manage an elderly relative's medical documents (tested in parallel). |
| Sample | 10 conversations per segment, 20 in total, chosen for feasibility within one semester |
| Format | 20–30 minute conversation in person or by call. Optional 10-minute usability task afterwards (section 7) |
| Data collected | First name (or a pseudonym) and one contact method, used only to schedule the session. Notes are de-identified with a code such as H2a-03. |
| Never collected | Medical reports, diagnoses, conditions, medicines, ABHA or Aadhaar numbers, account credentials, photos of documents |
| Usability material | **Fictional PDFs only**, supplied by the interviewer and marked "FICTIONAL SAMPLE" |
| Keeping results apart | Interview findings (sections 4–6) and usability results (section 7) are recorded and reported **separately** |

## 2. Screening questionnaire

Ask these questions before booking a session. Stop politely when someone is ineligible.

| # | Question | Eligible answer |
|---|---|---|
| S1 | Are you 18 or older? | Yes (otherwise end) |
| S2 | Do you live in or near Mumbai? | Yes for H1. Either answer for H2a. |
| S3 | Do you work for a hospital, lab, health app or insurer? | No (otherwise exclude, to avoid industry bias) |
| S4 | In the last 12 months, about how many times did you (not a family member) visit a lab or diagnostic centre for a test? *0 / 1 / 2–3 / 4 or more* | **2 or more → H2a** |
| S5 | In the last 12 months, have you helped an older family member keep or find their medical papers or reports? *Yes / No* | **Yes → H1** |
| S6 | Are you willing to talk for about 25 minutes about how you keep documents? We will not ask about any illness. | Yes |

If someone qualifies for both segments, put them in whichever segment still has fewer participants. Never ask which condition caused the tests.

## 3. Consent statement (read aloud, then record "Agreed: yes or no")

> "Thank you for your time. I am Rohit Srivastava, a second-year Computer Engineering student at RGIT, Mumbai. I am doing an Entrepreneurship Development project about how people keep and find medical documents.
> - Taking part is voluntary. You can skip any question or stop at any time, without giving a reason.
> - I will **not** ask about any illness, diagnosis or medicine. Please do not show or send me any real medical document.
> - I will write notes but not record audio, unless you agree to that separately. Your name will not appear in my report; I will use a code instead.
> - I will keep your contact details only until the project ends, and delete them after that.
> - If I show you a prototype, it will contain only made-up sample documents.
> - You can ask me to delete your notes at any time before the project is submitted, by contacting me.
>
> Do you agree to take part?"

If audio recording is wanted, ask a separate yes/no question.

## 4. Interview guide (past behaviour only, neutral wording)

Do not describe MediVault before question 12. Ask follow-ups such as "Can you tell me more?", "What happened next?" and "How did that feel?"

1. Tell me about the last time you received a medical report or prescription. How did it reach you: paper, WhatsApp, email, app, portal?
2. Where is that document now? How did it end up there?
3. Think of the last time you needed an older report, for example because a doctor asked for it. What happened?
4. How long did it take to find it? What did you try first?
5. Has there been a time in the past year when you could not find a report you needed? What did you do instead?
6. What did that cost you, if anything: time, a repeat test, money, a delayed appointment?
7. Which tools or places do you use to keep these documents today? Why those?
8. Have you heard of or used ABHA, DigiLocker, or any health-record app? What happened when you tried it? *(Ask only about experience; don't explain or promote them.)*
9. Do you currently pay for any storage or app to keep documents or photos? Which one, and why did you start paying?
10. *(H1 only)* How do you and your family member share their papers today? Who decides where things are kept?
11. What concerns, if any, would you have about keeping these documents in an online service?
12. *(Only now, a one-line neutral description)* "Some tools let you save each report with its date, type and provider, and search them later." What, if anything, would make that useful or not useful for you?
13. Is there anything about keeping or finding medical documents that I should have asked but didn't?

Do not ask "Would you use this app?" or "Do you like this idea?"

## 5. Short survey (optional, after the interview)

These questions are designed to avoid leading the respondent.

| # | Question | Answer format |
|---|---|---|
| Q1 | In the last 6 months, how many times did you need to find an old medical document? | 0 / 1 / 2–3 / 4+ |
| Q2 | The last time, roughly how long did it take? | Under 1 min / 1–5 min / 5–30 min / Over 30 min / Could not find it |
| Q3 | Where do you mainly keep them? (choose up to two) | Paper / Phone gallery / WhatsApp / Email / Cloud drive / Lab or hospital app / ABHA or DigiLocker / Other |
| Q4 | How well does your current method work for you? | Very poorly / Poorly / Neither / Well / Very well |
| Q5 | Do you pay for any cloud storage today? | Yes / No / Not sure |
| Q6 | How comfortable are you storing medical documents online? | Very uncomfortable … Very comfortable (5 points) |
| Q7 | Anything else? | Free text |

## 6. Interview note template (one per participant)

```
Participant code: ____  Segment: H2a / H1   Date: ____   Consent recorded: Yes / No   Audio: Yes / No
Screener: S4 = ____   S5 = ____
Last retrieval story (Q3–Q6), in the participant's words, de-identified:
Current tools (Q7):
ABHA / DigiLocker / app experience (Q8):
Pays for storage today? What and why (Q9):
Trust concerns, unprompted (Q11):
Reaction to the neutral description (Q12):
Surprises / new themes:
Coded themes (section 8): ____
Interviewer reflections (kept separate from what the participant said):
```

## 7. Usability task (fictional data only; results reported separately)

**Setup:**

- The interviewer runs MediVault (`d80c1bf`) locally on their own laptop. It is a local-only demo [REPO].
- A test account is pre-loaded with **fictional** records and PDFs labelled "FICTIONAL SAMPLE".
- The participant never types any real information.

**Before the first session**, check two things:
- **The local copy is the right version.** The founder's local copy was at `7eefd62` when this was written, and that version has no record-creation screen, so T1 can't be done on it. Bring the local copy up to `d80c1bf` first (a Git step that needs the founder's approval).
- **A keyset file exists.** `d80c1bf` refuses to start without a valid keyset file [S3]. Create a test-only keyset, kept outside the data folder, following the README's key-management steps.

| Task | Instruction | Measure |
|---|---|---|
| T1 | "Add this sample lab report as a new record and attach the sample PDF." | Completed? Time taken? Errors? |
| T2 | "Find the sample blood test from last March." *(the fictional record)* | Completed? Time to locate? |
| T3 | "Download the PDF attached to the sample prescription." | Completed? Time taken? |

Afterwards, ask: "What was confusing?" and "What did you expect to happen?"

Record each person's time to locate a record (T2). Compare it with the time they **self-reported** in Q2. That comparison is indicative only, not a controlled measurement.

**Usability result template:** participant code · T1/T2/T3 completed (Y/N) · seconds · errors · comments.

## 8. Coding framework

| Code | Theme | Example signal *(not real data)* |
|---|---|---|
| RET-FAIL | Could not find a document when it was needed | "I had to redo the test" |
| RET-SLOW | Found it, but slowly | Scrolled for a long time |
| SCATTER | Documents spread across many places | Paper plus WhatsApp plus email |
| TOOL-OK | Current method works well | Satisfied with the gallery or lab app |
| TOOL-FAIL | Current tool failed or frustrated them | App lost history, portal expired |
| ABHA-AWARE / ABHA-USE | Aware of or using government PHR tools | |
| PAY-NOW | Already pays for storage or an app | |
| TRUST | Privacy or trust concern | Worried about leaks, unknown provider |
| CARE-COORD | (H1) Coordination between family members | Siblings ask for reports |
| OTHER | Unexpected theme | |

## 9. Ranking pain points

For each code, count how many participants in each segment mentioned it (**frequency**). Then rate how much it cost them, using their own description: **severity** 1 to 3, where 1 = minor annoyance, 2 = lost time or a delay, 3 = a repeat test, extra cost or a missed appointment.

**Priority = frequency × average severity.** Report the top three per segment, with participant counts, for example "4 of 10". Never report percentages that hide how small the sample is.

## 10. Hypothesis table (thresholds are proposals, not results)

| ID | Hypothesis | Evidence collected | Proposed success threshold | Proposed failure signal | Status |
|---|---|---|---|---|---|
| HY1 | H2a participants often have trouble finding old reports | RET-FAIL / RET-SLOW in the last 6 months | ≥ 5 of 10 | ≤ 2 of 10 | **Pending** |
| HY2 | Current tools fail H2a participants | TOOL-FAIL | ≥ 3 of 10 | Most code TOOL-OK | **Pending** |
| HY3 | H1 caregivers have trouble finding old reports | RET-FAIL / RET-SLOW | ≥ 5 of 10 | ≤ 2 of 10 | **Pending** |
| HY4 | Current tools fail H1 caregivers | TOOL-FAIL | ≥ 3 of 10 | Most code TOOL-OK | **Pending** |
| HY5 | Some participants already pay for storage (a weak signal of willingness to pay) | PAY-NOW | No threshold; record the count only | n/a | **Pending** |
| HY6 | Trust is a major barrier | TRUST, unprompted | No threshold; record the count and themes | n/a | **Pending** |
| HY7 | The prototype is usable for the core tasks | T1–T3 completion | ≥ 80% of task attempts completed [PROP] | < 60% | **Pending** |

### Decision rules (proposed)

| Result | Decision |
|---|---|
| H2a meets HY1 + HY2 | Keep H2a |
| H2a fails and H1 meets HY3 + HY4 | Switch to H1; family profiles move to the top of the roadmap |
| Both meet their thresholds | H2a stays the MVP segment; H1 becomes the upgrade path |
| Neither meets them | Report this honestly and reframe the problem |

Ten conversations per segment give **indicative evidence only**.

## 11. Pilot plan (fictional data only)

1. **Days 1–30:** recruit using the screener, conduct the 20 interviews, code the notes.
2. **Days 31–60:** run the usability tasks with willing participants, using fictional PDFs on the founder's laptop.
3. **Days 61–90:** apply the decision rules and write up the findings, keeping interview and usability results apart.

No real medical document goes into MediVault at any stage. A pilot with real data would first need the hardening work described in `04-financial-model.md` and the legal review in `05-legal-privacy-risk.md`.

## 12. Handling the study's own data

- Store contact details apart from the notes, in a password-protected file.
- Notes use participant codes only.
- Delete contact details when the project ends.
- Honour any request to delete notes.
- Share only aggregated counts in the ED submission.

This protects participants' personal data. How the DPDP Act applies to a student study like this is **awaiting primary-source verification** (see `05-legal-privacy-risk.md`).
