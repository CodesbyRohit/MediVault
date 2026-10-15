# MediVault dashboard redesign — design spec

Date: 2026-10-14 · Author: Rohit Srivastava · Status: approved in principle; revision 2 (completeness,
storage quota, week boundaries, time zone, ordering, tests)
Basis: working tree as inspected on 2026-10-14 (latest local commit `d8f8655`; Git not run).

## 1. Goal

Turn the patient dashboard into a calm, premium, clinically precise overview built only from the
patient's own saved data. Every figure is real, owner-scoped, timestamped where a time exists, and
labelled with its source. No measurements, scores or text imply medical meaning or safety.

Success criteria:

- The dashboard shows the five agreed parts (§3) on desktop, tablet and phone without horizontal scroll.
- Every number traces to an owner-scoped query; another patient's data never appears or counts.
- No inline style, script or third-party asset (existing `TemplateCspComplianceTests` still pass).
- Existing behaviour and wording that tests rely on are unchanged: "Welcome, {name}", "Records by
  type", "You don't have any medical records yet.", the `href="/records/new">Add your first record</a>`
  link, and the existing `recordCount`, `recentRecords`, `typeCounts`, `storage` and
  `recentDocuments` model attributes.
- Charts are readable without colour, by screen readers, and in forced-colours mode.

## 2. Constraints (fixed)

- Stack unchanged: Java 17, Spring Boot 3.5, Thymeleaf, Spring Security, JPA. No new dependency,
  no web fonts, no JavaScript, no chart library.
- CSP (`SecurityConfig.CONTENT_SECURITY_POLICY`) unchanged: charts are server-rendered inline SVG
  using presentation attributes (`stroke-dasharray`, `stroke-dashoffset`, `height`, `y`, `transform`),
  never `style=`.
- No change to entities, Flyway migrations, authentication, authorization, CSRF, encryption,
  document storage, the quota or its enforcement, upload/edit/delete paths, or other pages' markup.
- Java additions are read-only and filtered by owner id.
- Data model facts: records hold title, type, record date, provider, notes and timestamps; the user
  holds full name, email and created-at. There are no health measurements, date of birth, blood
  group or allergies, so none are shown. Record types are Lab report, Prescription, Imaging,
  Discharge summary, Vaccination, Other — there is no "Consultation" type.

## 3. Components

All on `/dashboard` (`templates/dashboard.html`), patient role only.

### 3.1 Page header
Eyebrow "Dashboard", `h1` "Welcome, {fullName}", one-line subtitle "Every figure below is counted
from records you saved.", primary button "Add a record".

### 3.2 Three vault stat cards
Each card: label, large figure with unit word, a `<time>` line, and a "Source:" line.

| Card | Figure | Second line | Source line |
|---|---|---|---|
| Records stored | record count, "record"/"records" | "Last added {time of the most recently added record}" | "Source: your saved records" |
| Documents | PDF count, "PDF"/"PDFs"; meter of used ÷ limit | "{used} of {limit} upload limit used" and "Last upload {time}" | "Source: your attached PDFs (original file sizes)" |
| Latest record | record date of the latest record (d MMM yyyy), with its type and linked title | — | "Source: record date as you entered it" |

With zero records/documents the figure is 0 (or "—" for Latest record) and the time line reads
"No records yet" / "No PDFs yet".

**Deterministic ordering.**
- *Latest record* = the owner's record with the greatest `recordDate`; ties broken by greatest
  `createdAt`, then greatest `id`.
- *Last added* = the owner's record with the greatest `createdAt`; ties broken by greatest `id`
  (the same order the existing "Recently added" list uses).
- *Last upload* = the owner's document with the greatest `uploadedAt`; ties broken by greatest `id`
  (the existing `findRecentForOwner` order).

### 3.3 Storage figure — verified source and meaning
Verified in the code on 2026-10-14:
- The limit is the configured property `medivault.documents.quota-per-patient=100MB`
  (`application.properties:61`); no profile file overrides it. Spring's `DataSize` reads `100MB` as
  104,857,600 bytes, and `DocumentSizes.format` shows it as "100 MB".
- It is **enforced**: `RecordDocumentService.store` locks the patient's user row, then refuses an
  upload whose declared size, and again whose stored size, would take the total over the limit
  (`Reason.QUOTA_EXCEEDED`). Startup is refused if the limit is below the per-file maximum
  (`medivault.documents.max-size=10MB`). `DocumentQuotaTests` covers both refusal and
  cross-patient isolation.
- "Used" is `sum(size_bytes)` of the owner's documents, and `size_bytes` is the **original
  (plaintext) PDF size** (`DocumentStorage.StoredFile` javadoc), not the larger encrypted file on disk.

Display rule: show "{used} of {limit} upload limit used", with both values from the existing
`StorageUsage` (`usedLabel()`, `quotaLabel()`) and never hard-coded; the source line says "original
file sizes". If the property is ever removed or the check in `store` is dropped, the denominator
must be removed with it (this spec does not change either).

### 3.4 Patient summary card (navy surface)
- Initials badge (text, first letters of the first and last name parts), full name, "Patient account".
- "Member since {user.createdAt as a date in the server time zone, d MMM yyyy}".
- Record highlights (omitted when there are no records): "Records span {earliest recordDate} –
  {latest recordDate}", "{n} providers named" (distinct non-blank provider names), "{n} of 6 record
  types on file".
- Privacy line, only verified facts: "Only your account can open these records. New PDF uploads are
  stored encrypted." (Admins are denied `/dashboard` and `/records/**`; older files may be
  `PLAIN_V0`, hence "new".)

### 3.5 Record completeness ring
**Definition.**
- *provider-present records* = the owner's records whose `providerName` is non-null and not empty
  after trimming spaces.
- *notes-present records* = the same for `notes`.
- *PDF-present records* = the owner's records with **at least one** attached document; each record
  counts once however many PDFs it has (`count(distinct record id)`).
- **Completeness % = (provider-present + notes-present + PDF-present) ÷ (3 × total records) × 100**,
  rounded **down** to a whole number, so 100% appears only when every field is filled.
- **Zero records:** the value is undefined (no 0%, no ring). The ring is not rendered; the welcome
  panel (§4) takes its place.

The record form already strips whitespace and stores blank optional fields as `null`, so the
"trimming spaces" rule only matters for rows written outside the form.

**Display.** Inline SVG ring with three 120° segments separated by small gaps (provider, notes,
PDF); each has a pale track and a teal fill proportional to its count ÷ total. Centre: the overall
%. Text legend: "Provider named 8 of 12", "Notes added 6 of 12", "PDF attached 10 of 12". Caption:
"Paperwork completeness — not a health score." Segments do not link (no "missing field" filter exists).

### 3.6 Weekly histogram
**Time zone.** Weeks, dates and times on the dashboard use the JVM default time zone
(`ZoneId.systemDefault()`), as `RecordDocumentService` and `UserSummary` already do. `createdAt` is
stored as an instant (UTC), so the zone only changes how instants are grouped and printed. The
caption names the zone id, e.g. "Weeks start Monday (server time zone Asia/Kolkata)", because a
hosted server may run in UTC.

**Exact boundaries.** With `today = LocalDate.now(zone)`:
- `currentWeekStart` = `today`, or the Monday before it if `today` isn't a Monday.
- `firstWeekStart` = `currentWeekStart − 7 weeks`.
- Window = from `firstWeekStart 00:00` (inclusive) to `(currentWeekStart + 1 week) 00:00`
  (exclusive), both as local start-of-day in `zone`.
- A record counts in the week whose Monday is the Monday on or before its `createdAt` date in `zone`.
- Bars are always 8, oldest first, empty weeks shown as 0. The last bar is the current, partial
  week and is labelled "this week, so far" in the data table.
- Example: on Wednesday 14 Oct 2026 in Asia/Kolkata the weeks start 24 Aug … 12 Oct, the window is
  2026-08-23T18:30Z (incl.) to 2026-10-18T18:30Z (excl.), and a record added Sunday 11 Oct 23:59:59
  IST falls in the week of 5 Oct while one added Monday 12 Oct 00:00 IST falls in the week of 12 Oct.
- Daylight saving: weeks are 7 calendar days in `zone`, so a week containing a clock change is
  167 or 169 hours long; nothing is double-counted or lost.

**Display.** Inline SVG bars scaled to the largest week (scale at least 1; a non-zero week is at
least 2% tall so it stays visible), the count above each bar, the week-start label ("6 Oct") below.
Caption: "By date added to MediVault, not the record date."

### 3.7 Category breakdown
Heading "Records by type". All six types in enum order with count, a bar and a whole-number share
of all records (rounded half up; shares need not sum to 100); zero rows muted. Each type name links
to the existing filter `/records?type={TYPE}`.

### 3.8 Recent lists (kept)
"Recently added" (3 records) and "Recently uploaded" (5 PDFs) stay with their current content and
ordering, restyled. They are hidden when the patient has no records.

## 4. States

- **New patient (0 records):** header, summary card (no highlights), stat cards at zero, and one
  welcome panel in place of ring, histogram, categories and recent lists, containing the existing
  text "You don't have any medical records yet." and link "Add your first record".
- **Loading:** none — the page is fully server-rendered with no scripts; no skeletons are added.
- **Error / restricted:** `error.html`, `error/403.html`, `error/404.html` keep their markup and pick up
  the refined tokens.
- **Success:** existing flash messages keep their markup and pick up the refined tokens.

## 5. Layout

DOM order (also the phone order): header, three stat cards, summary, completeness, histogram,
categories, recently added, recently uploaded.

- ≥66rem: 12-column grid. Summary placed in columns 1–3, rows 1–2; stat cards 3 columns each in row 1;
  completeness (4) and histogram (5) in row 2; categories, recently added, recently uploaded (4 each)
  in row 3. Container widened to 76rem on the dashboard only.
- 40–66rem: 6-column grid. Stat cards 2 columns each; summary, completeness, histogram and
  categories full width (so histogram labels stay legible); recent lists 3 each.
- <40rem: one column, 16px side gutter; SVGs scale with `viewBox` and `width: 100%`. Long names and
  titles wrap (`overflow-wrap: anywhere`); no horizontal scroll.

The summary card has no focusable content, so its desktop position does not change focus order.

## 6. Visual system

Refine, don't replace, the `:root` tokens in `static/css/main.css`.

- Colour: navy foundation; `--color-navy-soft` summary card with `--color-on-dark-muted` and accent
  text (contrast already documented on those tokens). Data ramp: `--color-data-1: #0b5f7a` (7.2:1 on
  white), `--color-data-2: #0e7c86` (≈4.9:1), `--color-data-3: #2a9d8f` (≈3.3:1), track
  `--color-data-track: #eef5f8` (decorative; the lightest fill still reaches ≈3.0:1 against it).
  Marks meet 3:1; text stays on existing text tokens.
  Contrast values are computed with the WCAG formula during implementation and written beside each token.
- Surfaces: neutral backgrounds, 1px hairline borders, existing translucent glass on stat cards only.
- Type: existing system font stack; card titles 1rem semibold; stat 2.25rem; captions 0.8125rem;
  `font-variant-numeric: tabular-nums` on all figures.
- Spacing: existing `--space-*` scale; one card padding and `--radius-surface`.
- Motion: none on charts; existing 120 ms colour transitions remain, disabled under reduced motion.

## 7. Accessibility

- Ring SVG: `role="img"` with `aria-label` "Overall record completeness {n} percent"; the visible
  legend carries the per-field counts as text.
- Histogram SVG: `aria-hidden="true"`; a visually hidden `<table>` (caption, "Week starting",
  "Records added") carries all eight values, so screen readers hear each value once.
- Category bars stay `aria-hidden` `<meter>`s with the count and share as text.
- Each card is a `section` with `aria-labelledby`; heading order h1 → h2.
- Visible focus on all links/buttons using the existing focus tokens.
- Forced-colours, high-contrast and reduced-transparency media blocks extended to the new elements
  (chart marks fall back to `Highlight`/`GrayText`, the summary card to `Canvas`/`CanvasText`).

## 8. Java changes (read-only)

- `MedicalRecordRepository`: owner-scoped derived/JPQL queries — provider-present count, notes-present
  count, `createdAt` instants in `[from, to)`, earliest record (by `recordDate` asc, `id` asc), latest
  record (by `recordDate` desc, `createdAt` desc, `id` desc), last added (by `createdAt` desc, `id` desc).
- `RecordDocumentRepository`: `count(distinct record id)` for the owner; the existing
  `findRecentForOwner(ownerId, PageRequest.of(0, 1))` gives the last upload.
- `UserRepository`: inherited `findById(ownerId)` with the principal's own id, for name and created-at.
- New `WeekWindow` (pure: boundaries and bucketing), `DashboardInsights` (record with nested
  `Completeness`, `RingSegment`, `WeekBar`, `TypeShare`) and `DashboardService` (read-only, assembles
  the insights). `RecordController.dashboard` adds one model attribute, `insights`. Existing model
  attributes stay.

## 9. Testing

Run only after approval:

- Unit (`WeekWindowTests`): window start/end for a Wednesday, a Monday and a Sunday; Sunday 23:59:59
  vs Monday 00:00 in the zone; exactly-at-start included and exactly-at-end excluded; the same instant
  in two zones landing in different weeks; a Europe/London daylight-saving week; bar heights.
- Unit (`DashboardInsightsTests`): completeness formula, floor rounding, zero-record `null`, ring
  geometry, type shares, initials.
- Integration (`DashboardTests`, H2): completeness counts one PDF-present per record with two PDFs
  attached and ignores space-only provider/notes; the eight-week window via the database (just before
  start excluded, start included, Sunday/Monday split, end excluded) in a fixed zone; another
  patient's records, PDFs, providers, types, latest record and weekly activity never count; latest
  record and last added follow the tie-break order; a new patient gets empty insights and the
  unchanged empty-state page; the page shows the configured limit and server zone id.
- Existing `TemplateCspComplianceTests` and the full `.\mvnw.cmd test` suite pass.
- Visual check in offline headless Chrome (dead proxy, resolver block, 1 s connection monitor) at
  1440, 900 and 390 px widths, empty and populated fictional patients.

## 10. Out of scope

Health-measurement tracking, a "missing field" records filter, any change to the storage limit or
its enforcement, redesigning other pages' markup, self-hosted fonts, JavaScript, dark mode, any Git,
deployment or system change.
