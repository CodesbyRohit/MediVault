# MediVault: Milestone 1 Requirements

| | |
|---|---|
| **Project** | MediVault |
| **Author** | Rohit Srivastava |
| **Course context** | Java + Entrepreneurship Development (ED) mini-project |
| **Status** | **APPROVED.** Decisions D1–D5 were resolved on 2026-10-10 (section 12). Persistent local storage and PDF documents were added on 2026-10-11: D7 and D8 **approved and implemented** (section 12); D8's MySQL part is not verified yet. |
| **Baseline** | `main` @ `3ab031a` (Spring Boot 3.5.16 foundation) |
| **Date** | 2026-10-10 |

---

## 1. Entrepreneurship Development (ED) perspective

> This section covers the entrepreneurial side of the project only. It contains **no market statistics, financial projections or customer-validation claims**. None have been researched or collected. MediVault is a student mini-project, not a commercial product.

### 1.1 Problem statement
People's medical documents (lab reports, prescriptions, scan reports, discharge summaries, vaccination certificates) tend to be scattered across paper files, phone galleries, messaging apps and different clinics. As a result:
- reports get lost or are hard to find when a doctor asks for them,
- it is difficult to give a new doctor an accurate, ordered medical history,
- people may have no single, private place that belongs to *them* rather than to a particular hospital.

### 1.2 Target users
- **Primary:** individuals who want to keep their own medical records organised in one place.
- **Secondary (future):** family caregivers, such as an adult child managing an elderly parent's reports.
- **Not targeted:** hospitals and clinics as institutional customers.

### 1.3 Value proposition
One private, organised place for a person's own medical record history. It is accessible from any browser, private by default, and visible only to its owner, not even to administrators.

### 1.4 Proposed solution
A secure web application where a person creates an account and keeps a structured list of their medical records (title, type, date, provider, notes), with strict owner-only access, and can attach the actual PDF documents to those records (D8).

### 1.5 Feasibility within this mini-project
- **Technically feasible with the chosen stack.** Java 17, Spring Boot, Thymeleaf, Spring Security, JPA and MySQL are mature, free and well documented. No paid services are required.
- **Scope is deliberately small.** Milestone 1 covers accounts, security and a read-only record list. Uploads, sharing and integrations are deferred.
- **Main constraint:** health data is sensitive. That is why the design puts privacy, ownership checks and fictional demo data first, instead of building many features.

### 1.6 Demonstration scope (Milestone 1, implemented features only)
1. A visitor opens the landing page and learns what MediVault is.
2. They register as a patient and log in.
3. They see a personal dashboard and their own records list. With the opt-in demo mode (`demo` together with `local-file` or `local-h2`), these are 3 clearly labelled fictional sample records.
4. They attach a fictional PDF to one of their records and download it again (D8). With `local-file`, it is still there after the app restarts (D7).
5. A second patient cannot see the first patient's records or documents, even by changing IDs in the URL (404).
6. An administrator sees a user list but no medical content or documents.
7. The user logs out.

### 1.7 Potential future business opportunities (hypotheses, not implemented and not validated)
- A **freemium** personal-records service, with paid options such as larger storage or family accounts.
- **Family or caregiver plans** for managing records of dependants.
- **Controlled, time-limited sharing** with a doctor, chosen by the patient.
- **Partnerships with diagnostic labs** to deliver reports straight into a patient's vault, if regulation allows.

Any of these would require user research, legal and regulatory review of health-data rules, and security hardening beyond this project.

> All demo and test data is fictional. MediVault must never contain real patient data during this project.

---

## 2. Users and roles

| Role | How the account is created | Purpose |
|---|---|---|
| `PATIENT` | Self-registration on `/register`. This is the **only** role registration can produce. | Manages their own records |
| `ADMIN` | **Never** through registration. Created only by the startup bootstrap (D1). | Oversees accounts, not medical content |
| Anonymous | (not logged in) | Can view public pages only |

### Permission matrix

| Capability | Anonymous | PATIENT | ADMIN |
|---|:-:|:-:|:-:|
| View landing page, login page and registration page | ✅ | ✅ | ✅ |
| Register a new account | ✅ | ❌ (redirected to dashboard) | ❌ (redirected to admin) |
| View own dashboard (`/dashboard`) | ❌ → login | ✅ | ❌ 403 |
| List and view **own** medical records | ❌ → login | ✅ | ❌ 403 |
| View **another patient's** records | ❌ → login | ❌ 404 | ❌ 403 |
| Upload a PDF to / download a PDF from an **own** record | ❌ → login | ✅ | ❌ 403 |
| Upload to / download from **another patient's** record | ❌ → login | ❌ 404 | ❌ 403 |
| View the user list (`/admin`) | ❌ → login | ❌ 403 | ✅ |
| Any URL not listed above | ❌ → login | ❌ 403 | ❌ 403 |
| Log out | n/a | ✅ | ✅ |

**Assumptions.**
- **A1.** Admins manage accounts but **cannot read medical record content**, following the least-privilege principle for health data.
- **A2.** One account has exactly one role.
- **A3.** An admin who opens patient pages gets `403`. Roles are kept separate rather than nested.

---

## 3. Milestone 1 features and routes

| Route | Method | Access | Purpose |
|---|---|---|---|
| `/` | GET | Public | Landing page: what MediVault is, its value, and a "fictional data only" notice |
| `/register` | GET, POST | Anonymous | Registration form with server-side validation |
| `/login` | GET | Public | Custom login page. Spring Security handles `POST /login`. |
| `/logout` | POST | Authenticated | Logout, CSRF-protected. Redirects to `/login?logout`. |
| `/dashboard` | GET | PATIENT | Greeting, record count, 3 most recent records |
| `/records` | GET | PATIENT | The patient's own records, newest first, with an empty-state message |
| `/records/{id}` | GET | PATIENT (owner only) | Record detail, the record's documents and the upload form |
| `/records/{id}/documents` | POST (multipart, CSRF) | PATIENT (owner only) | Upload one PDF (D8). Redirects back to the record with a success or error message. |
| `/records/{id}/documents/{documentId}` | GET | PATIENT (owner only) | Stream the PDF as an attachment. 404 unless the document belongs to that record and the record to the user. |
| `/admin` | GET | ADMIN | Read-only user list: name, email, role, enabled, created date. No record content. |
| `/css/**`, `/images/**` | GET | Public | Static assets |
| `/favicon.ico` | GET | Public | Requested automatically by browsers. There is no file at this path, so it returns 404; pages link `/images/favicon.svg` instead. Kept public so the browser's automatic request isn't redirected to login. |
| `/error` | (any) | Public | Spring Boot's error page. Also rendered through internal **ERROR dispatches**, which are the only internal dispatches exempt from authorisation. It shows only a status code and a generic message. |

**Internal forwards are not exempt.** An internal `FORWARD` dispatch is authorised like a direct request, so it cannot be used to reach a protected route.

Records cannot be created through the UI in Milestone 1. They exist only through the opt-in demo mode (`local-h2` + `demo`, D3) or in automated tests.

---

## 4. Data model

```
┌──────────────────────┐ 1        * ┌──────────────────────────┐ 1      * ┌───────────────────────────┐
│ User  (app_users)    │────────────│ MedicalRecord            │──────────│ RecordDocument            │
│                      │   owner    │ (medical_records)        │          │ (record_documents)        │
└──────────────────────┘            └──────────────────────────┘          └───────────────────────────┘
        Milestone 1                         Milestone 1                     Implemented 2026-10-11 (D8)
```

### 4.1 `User` (table `app_users`; `user` is a reserved word in H2 and MySQL)

| Field | Type | Constraints and validation |
|---|---|---|
| `id` | `Long` | PK, identity |
| `fullName` | `String` | Required, trimmed, 2–100 chars |
| `email` | `String` | Required, valid email, max 254 chars. Stored **trimmed and lower-cased**, **unique** DB constraint. Used as the login username. |
| `passwordHash` | `String` | Required. BCrypt hash, column length 100. The raw password is never stored or logged. |
| `role` | `Role` enum (`PATIENT`, `ADMIN`) | Required, stored as a string. Always `PATIENT` from registration. |
| `enabled` | `boolean` | Default `true`. Disabled accounts cannot log in. |
| `createdAt`, `updatedAt` | `Instant` | Set automatically |

### 4.2 `MedicalRecord` (table `medical_records`)

| Field | Type | Constraints and validation |
|---|---|---|
| `id` | `Long` | PK, identity |
| `owner` | `User` | `@ManyToOne(fetch = LAZY)`, **required**, indexed FK. It is a one-directional link: `User` holds no list of records. |
| `title` | `String` | Required, 3–150 chars |
| `recordType` | `RecordType` enum | Required: `LAB_REPORT`, `PRESCRIPTION`, `IMAGING`, `DISCHARGE_SUMMARY`, `VACCINATION`, `OTHER` |
| `recordDate` | `LocalDate` | Required, not in the future |
| `providerName` | `String` | Optional, max 150 chars |
| `notes` | `String` | Optional, max 1000 chars |
| `createdAt`, `updatedAt` | `Instant` | Set automatically |

### 4.3 `RecordDocument` (table `record_documents`; implemented 2026-10-11, D8)

This keeps **document metadata separate from the PDF file itself**. Rows are never updated after upload.

| Field | Type | Constraints and purpose |
|---|---|---|
| `id` | `Long` | PK, identity |
| `record` | `MedicalRecord` | `@ManyToOne(fetch = LAZY)`, required, indexed FK `fk_record_documents_record`. Ownership is inherited through the record. |
| `originalFilename` | `String` | Required, max 255 chars. Sanitised (last path segment, no control or reserved characters). Display and download name only; never used as a filesystem path. |
| `storageKey` | `String` | Required, 36 chars, **unique**. A random UUID (version 4); the file's only name in **private storage outside the web root**. Never shown or accepted from users. |
| `contentType` | `String` | Always `application/pdf`, set only after the extension, the declared type **and** the content (PDF header and `%%EOF` marker) were checked |
| `sizeBytes` | `long` | Required, `CHECK (size_bytes > 0)`, at most 10 MB (`medivault.documents.max-size`) |
| `sha256` | `String` | Required, 64 hex chars; integrity check. Not shown in the UI. |
| `uploadedAt` | `Instant` | Set automatically |

The PDF bytes never go into the database and are never served as static files. They are streamed only through an authorised controller that re-checks ownership. **There are no public PDF URLs.** Filesystem writes and the database transaction are not atomic: the file is written first (to a `.part` file, then renamed without ever replacing an existing file), and a transaction callback deletes exactly that file again unless the metadata row commits. A crash between the rename and the commit can leave an unreferenced file, which is never served (README, "Interrupted uploads").

### 4.4 Ownership rules

1. **R1.** Every patient-facing record query is scoped to the logged-in user's ID, using `findAllByOwnerId…` and `findByIdAndOwnerId(id, ownerId)`. Document queries are scoped by record ID **and** the record's owner ID (`findByIdAndRecordIdAndRecordOwnerId`). Patient flows never call a plain `findById`.
2. **R2.** The owner ID always comes from the **server-side security context**, never from a request parameter, form field or URL.
3. **R3.** A request for a record the user doesn't own returns **404 Not Found**, so the response doesn't reveal that the record exists.
4. **R4.** Forms bind to dedicated form objects (such as `RegistrationForm`), never directly to entities. The binder also allow-lists form fields. This prevents mass assignment of `role`, `enabled`, `owner` or `id`.

---

## 5. Security requirements

| # | Requirement |
|---|---|
| S1 | Spring Security with an explicit `SecurityFilterChain`. **Default-deny:** any URL not listed is denied. Anonymous users are sent to login, and logged-in users get 403. Only `ERROR` dispatches are exempt, so internal `FORWARD`s are authorised normally. |
| S2 | Passwords hashed with `BCryptPasswordEncoder` (strength 10). Policy, applied to both registration and admin bootstrap: **8–64 characters, at least one letter and one digit**, and no more than 72 bytes in UTF-8 (BCrypt's input limit). Password and confirmation must match. |
| S3 | A database-backed `UserDetailsService` that logs in by email. Defining it switches off Spring Boot's generated default password. |
| S4 | Server-side role checks at the URL level (`/admin/**` → ADMIN, patient routes → PATIENT). Ownership is also checked in the service layer (section 4.4), so hiding links in the UI is never the only protection. |
| S5 | Safe registration defaults: the role is always `PATIENT` and accounts are enabled. Unexpected parameters such as `role=ADMIN` are ignored. |
| S6 | **CSRF protection stays on.** Every POST form includes the token automatically through Thymeleaf `th:action`. Logout is POST-only. |
| S7 | A failed login always shows the same message, "Invalid email or password", whether the email exists or not. Disabled accounts get the same message. |
| S8 | The session ID changes at login (session-fixation protection). Logout invalidates the session and deletes the session cookie. The session cookie is `HttpOnly` with `SameSite=Lax`. The `Secure` flag is deferred until HTTPS deployment, which is out of scope. |
| S9 | HTTP Basic is disabled. Spring's default security headers stay on. A strict `Content-Security-Policy` allows only same-origin resources and no inline scripts. |
| S10 | Error pages (`403`, `404`, generic) are custom templates. Stack traces, exception messages and binding errors are never included, even when DevTools is active. |
| S11 | Passwords and password hashes never appear in logs, `toString()` output, model attributes or redirects. The logged-in user object drops the hash once login completes. |

### D1: admin bootstrap (resolved)
- An admin is created **only** when both `MEDIVAULT_ADMIN_EMAIL` and `MEDIVAULT_ADMIN_PASSWORD` are set as environment variables. No default admin exists, and no credentials are committed.
- The email is normalised (trimmed, lower-case) and validated, and the password must meet the S2 policy. If only one variable is set, or either value is invalid, **no account is created**. A warning names the problem but never the values, and the application keeps running.
- If an account with that email **already exists**, whether patient or admin, **nothing changes**. Its password and role are never overwritten on restart.
- Only the BCrypt hash is stored. Logs never contain the email, password or hash, only the new account's numeric ID.
- **Lifecycle note:** changing `MEDIVAULT_ADMIN_EMAIL` to a new address creates an **additional** admin on the next start. The previous admin stays active, because the bootstrap deliberately never disables, deletes or changes existing accounts. Removing an old admin is a manual database task until admin account management exists (not in Milestone 1).

### D4: duplicate email (resolved)
Registration shows **"An account with this email already exists."** This is a **known limitation**: it lets anyone check whether an email is registered (email enumeration). It is acceptable for this coursework demo, but **not suitable for a publicly deployed healthcare product** without further protection, such as a neutral message with email confirmation, plus rate limiting.

---

## 6. Storage and environments

| Context | Database | How it is selected |
|---|---|---|
| Automated tests | **H2 in-memory** `medivault_test` (MySQL mode) | Profile `test`, forced through `@TestPropertySource`, which takes precedence over `SPRING_DATASOURCE_*` and `MEDIVAULT_ADMIN_*` environment variables and over the `local-h2` profile file. MySQL is never needed. |
| Persistent local development (D7) | **H2 file** `<root>\data\medivault` (MySQL mode); PDFs in `<root>\records\`. `<root>` = `MEDIVAULT_STORAGE_ROOT`, otherwise `%LOCALAPPDATA%\MediVault`. | Explicit profile `local-file` (`application-local-file.properties`). Flyway H2 migrations (`db/migration/h2`) + Hibernate `validate`; nothing is ever dropped. The guard accepts only a `jdbc:h2:file:` database directly inside `<root>\data`, PDFs in `<root>\records`, allow-listed H2 settings and real (non-linked) folders; `local-file` cannot be combined with `local-h2`, `test` or `mysql`. File-backed H2 is refused under every other profile. |
| Local UI demo (D5) | **H2 in-memory** `medivault_local` (MySQL mode) | Explicit profile `local-h2` (`application-local-h2.properties`). Data resets on every restart, and nothing is written to disk. The H2 web console stays disabled in every profile. **Overrides:** environment variables take precedence over profile files, so a `SPRING_DATASOURCE_URL` variable replaces this profile's URL. If the result is not an in-memory H2 URL (`jdbc:h2:mem:`), for example MySQL, the H2 startup guard **refuses to start** instead of connecting. Clear the `SPRING_DATASOURCE_*` variables first. `DB_*` variables are ignored by this profile. |
| Intended application database | MySQL **8.x** (`DB_*` env vars) | Default configuration (`application.properties`). Set up separately later. **No installed database service is changed or stopped.** |

- **D5 (resolved):** H2's Maven scope changed from `test` to `runtime`, the minimal change that makes the `local-h2` profile possible. H2 is now included in the packaged application.
- **H2 startup guard:** `DatabaseSafetyGuard` (called `H2DatabaseGuard` in Milestone 1; extended in Milestone 2 with database-profile and MySQL connection rules) runs before the application context is created and **refuses to start** in these H2 cases:
  1. The datasource is H2 (an H2 URL, or an empty URL, which Spring Boot would turn into an embedded H2 database) but neither the `local-h2` nor the `test` profile is active. A deployment is therefore never silently switched to an in-memory database.
  2. The `local-h2` profile is active but the datasource is not in-memory H2 (`jdbc:h2:mem:`), for example because `SPRING_DATASOURCE_URL` overrides it with MySQL. `local-h2` therefore never silently runs against a real database.

  Neither error message includes the JDBC URL, because URLs can contain credentials.
- **Connection settings, every profile (M4 audit, 2026-10-11):** only `spring.datasource.url` may choose the database. JNDI and non-HikariCP pool types are refused; `spring.datasource.hikari.*` is limited to pool sizing and timeouts, and `spring.flyway.*` to an allow-list (a separate `spring.flyway.url` only with `mysql`). Persistent profiles may only migrate from `classpath:db/migration/{vendor}`. Before this rule, driver properties (e.g. an H2 `INIT` script), connection SQL, a separate Flyway URL under `local-h2`, or a filesystem migration location could bypass the URL, port and schema rules.
- **D3 (resolved), fictional demo records:** created **only when `demo` is active together with `local-h2` or (since D7) `local-file`**. Because the guard pins both local profiles to H2, demo records cannot be written to MySQL: a local profile with a MySQL override is rejected at startup. If `demo` is set without a local profile, no records are created and a warning says so. When demo mode is active, each patient who registers gets 3 clearly labelled fictional sample records, created once at registration. A guard skips any account that already has records. Existing accounts are not backfilled. A warning is logged at startup whenever demo mode is active.
- **Schema:** Hibernate `create-drop` for `test` and `local-h2`. Flyway versioned migrations plus Hibernate `validate` for `local-file` (`db/migration/h2`) and `mysql` (`db/migration/mysql`); V2 adds `record_documents` to both. The MySQL V2 migration has not been run against MySQL yet.
- **Milestone 2 (in progress; reordered by decision D5):** **Flyway** migrations plus Hibernate **`validate`** in a dedicated `mysql` profile, replacing `ddl-auto=update`, so a real database is never changed implicitly. The code foundation exists; it is not verified until migrations and validation run against the isolated MySQL 8.4 instance.

---

## 7. UI requirements

- **Shared layout:** one Thymeleaf layout template that pages fill in, using `th:replace` with fragment parameters, with no layout library added. It has a common `<head>`, header and navigation, a demo banner, the main content area and a footer crediting Rohit Srivastava.
- **Navigation** changes with login state, using `sec:authorize` from the existing `thymeleaf-extras-springsecurity6` dependency. The current page is marked with `aria-current="page"`.
- **Accessibility:**
  - semantic landmarks and a "Skip to main content" link
  - a unique `<title>` per page and `lang="en"`
  - every input has a visible `<label>` and an `autocomplete` value
  - field errors are linked with `aria-describedby` and `aria-invalid`, and an error summary at the top links to each field
  - visible focus and WCAG AA colour contrast
  - meaning is never shown by colour alone
- **Validation feedback:** server-side messages appear next to each field. Entered values except passwords are kept. Success messages appear after registration and logout.
- **Responsive styling:** one hand-written, mobile-first `static/css/main.css`, with no framework or CDN. It works from 320 px upwards, and tables become stacked cards on narrow screens.
- **JavaScript:** none.

---

## 8. Testing requirements (H2, `@SpringBootTest` + MockMvc + `spring-security-test`)

| Area | Test cases |
|---|---|
| Context and isolation | The context starts on profile `test`. The datasource is H2 `medivault_test`. Spring's in-memory default user does not exist, and the database-backed `UserDetailsService` is used. |
| Registration validation | A valid form creates exactly one enabled `PATIENT` and redirects to login. Invalid fields re-render with field errors and save nothing. A duplicate email, including with different letter case, shows the D4 message. A POST without CSRF gets 403. |
| Password hashing | The stored value is a BCrypt hash that matches the raw password and is not equal to it. |
| Role injection | A `role=ADMIN` parameter still creates a `PATIENT`. |
| Login and logout | Patient → `/dashboard`, admin → `/admin`. A wrong password, an unknown email and a disabled account all go to `/login?error` with the generic message. Logout invalidates the session. |
| Access control | Anonymous users are redirected to login. A patient gets 403 on `/admin`. An admin gets 403 on patient routes. Unknown routes are denied. |
| Record ownership | A patient sees only their own records. Another patient's record ID returns 404. |
| Admin bootstrap | Created only when both values are valid. The password is hashed. Nothing is overwritten on re-run. An existing patient's email is never elevated to admin. Logs contain no credentials. |
| Demo records | 3 fictional records only with `demo` **and** `local-h2`, none for any other profile combination, and never duplicated. |
| Database safety | H2 is refused at startup without `local-h2` or `test`, and so is an empty URL. MySQL URLs are allowed without `local-h2`. `local-h2`, with or without `demo`, is refused unless the URL is in-memory H2, and the message never reveals the URL. A real startup is stopped before any context exists. |
| Session and dispatch | The session ID changes at login, in both MockMvc and a real server. The real session cookie is `HttpOnly` and `SameSite=Lax`. An internal forward from a public URL to `/admin` is refused (anonymous → login, patient → 403). |
| Layout | The landing, login and register pages render with the shared layout, navigation, CSRF tokens and the CSP header. |
| Persistent local storage (D7) | A real `local-file` start creates `<root>\data` and `<root>\records` in a temporary root, applies migrations V1–V2, and validates. Accounts, records and PDFs survive a restart, and the database file is reused. Demo samples are not duplicated on restart. The default root is `%LOCALAPPDATA%\MediVault` and `MEDIVAULT_STORAGE_ROOT` overrides it (checked on the prepared environment, without starting). Overrides outside the root, unsafe H2 settings, linked folders, destructive schema settings and profile conflicts are refused before anything is created. The developer's real storage root is unchanged after the tests. |
| Upload validation (D8) | A fictional PDF is accepted. Empty, wrong-extension, wrong-declared-type, spoofed (executable or HTML named `.pdf`), truncated and over-10-MB uploads are rejected with a safe message and leave no file. Storage keys are random UUIDs; non-UUID and traversal keys are refused; links are never followed; an existing file is never overwritten. |
| Document authorization (D8) | The owner can list and download. Another patient gets 404 by changing the record or document ID. An admin gets 403. Anonymous users are sent to login. Uploads without a valid CSRF token are refused, including through a real Tomcat with a browser-style multipart body. |
| Document failures (D8) | A storage failure shows a generic message. A failed database insert deletes only the newly written file, and only after a definite rollback (an unknown outcome keeps the file). A file missing on disk gives a generic 500 page (HTML and JSON) without paths, keys or exception details. Range and HEAD requests close the stored file. |
| Connection-setting allow-lists (M4) | In every profile, disallowed `spring.datasource.hikari.*` / `spring.flyway.*` settings (including relaxed environment-variable names), JNDI, other pool types and a non-`mysql` Flyway URL are refused before any context exists; in-memory profiles redirected through them never reach a canary socket; `local-file` creates nothing. |

---

## 9. Acceptance criteria (observable)

| Feature | Accepted when… |
|---|---|
| Landing page | `/` loads without login, describes MediVault, shows the fictional-data notice and links to Register and Login, and is usable at 320 px width. |
| Registration | A valid submission creates exactly one `PATIENT` account with a BCrypt hash and shows "Registration successful. Please log in." Each invalid field shows a specific message, the form keeps the non-password values, and no account is created. |
| Login | Valid credentials take a patient to `/dashboard` and an admin to `/admin`. Invalid ones show "Invalid email or password". |
| Logout | Logout ends the session and shows "You have been logged out." Protected pages then require login again. |
| Dashboard | It shows "Welcome, ⟨full name⟩", the user's own record count and up to 3 recent records. It's reachable only by a PATIENT. |
| Records list and detail | Only the logged-in patient's records appear, newest first, with an empty state when there are none. Another patient's record ID returns 404. |
| Admin page | Only an ADMIN can open it. It shows a user list with no medical record content. |
| Admin bootstrap | With both environment variables valid, one admin exists after startup. Restarting changes nothing. No credentials appear in logs. |
| Demo records | With `local-h2,demo` active, a new patient sees exactly 3 records labelled "(fictional)". With any other combination, a new patient sees none. |
| Security baseline | No generated Spring password appears in logs. CSRF is active on all POST forms. All tests pass with `.\mvnw.cmd test` and no MySQL. |
| Accessibility | Every form can be completed by keyboard alone, every input has a label, and errors are linked to their fields. |

---

## 10. Out of scope for Milestone 1 and known limitations

**Out of scope:**
- Real patient data, and public or shareable PDF links
- Deleting or replacing uploaded documents through the UI; virus scanning or sanitising PDFs; encryption at rest
- Creating, editing and deleting records through the UI
- Production deployment, HTTPS, Docker and cloud infrastructure
- AI, OCR and email or SMS integrations
- Password reset, remember-me, email verification and MFA
- Admin actions such as enable/disable or delete, sharing, QR codes, audit logs and localisation

**Known limitations (accepted for this coursework demo):**
- **Email enumeration** through the registration message (D4).
- **No login rate-limiting or account lockout.** Brute-force protection is needed before any public deployment.
- **No HTTPS**, so the session cookie is not marked `Secure`.
- **`local-h2` data is lost on restart**, by design. Its data is never migrated to `local-file`.
- **No per-patient storage quota.** Each PDF is limited to 10 MB, but there is no limit on the number of documents or the total size per patient, so one account can fill the disk. An aggregate per-patient quota is a **future requirement** (deferred in the M4 review, 2026-10-11).
- **Disabled-account session invalidation is deferred** until account disabling exists. A disabled account cannot log in (S7), but a session that was already open is not ended when `enabled` changes. No admin screen can disable accounts yet, so this cannot happen through the application today.
- **Logging:** Spring Boot's startup-info lines (Windows user name, PID, project/JAR path) are switched off in every profile (`spring.main.log-startup-info=false`). A clean `local-file` run checked on 2026-10-11 logged no paths, JDBC URLs, storage keys, passwords, tokens or stack traces. Spring's `Resolved [...]` warnings, which could echo a malformed request value or the requested path, are switched off: `DefaultHandlerExceptionResolver` logged them in every profile and DevTools switched them on for the other resolvers (`spring.mvc.log-resolved-exception=false`, `DefaultHandlerExceptionResolver` and `PageNotFound` log levels raised to `ERROR`). A sanitised line is logged instead in every profile (`RejectedRequestLogger`: exception type, route template, standard HTTP method, parameter name and expected type, never request values). Unhandled errors (500) are still logged with their exception by the servlet container. Other logging configurations have not been reviewed.
- **`local-file` is single-computer storage without encryption**: the H2 file (built-in `sa` account, no password) and the PDFs are protected only by Windows account permissions. Backups must copy the database and the `records` folder together while the app is stopped.
- **The MySQL 8.4 configuration** has not yet been verified against the real isolated MySQL 8.4 server. Milestone 2 replaces `ddl-auto=update` with Flyway plus `validate` (`mysql` profile).
- **Changing `MEDIVAULT_ADMIN_EMAIL`** leaves the previous admin active (section 5, D1).
- **Manual browser checks are still required before the faculty demo:** 320 px layout, keyboard-only navigation, and no Content-Security-Policy violations in the browser console.

---

## 11. Proposed roadmap (for context only, not approved)

| Milestone | Scope |
|---|---|
| **M1 (this document)** | Layout, landing page, registration, BCrypt, login/logout, dashboard, own-records list, admin user list, role and ownership tests |
| **M2 (in progress, D5)** | MySQL 8.4 integration: isolated instance on `127.0.0.1:3307`, Flyway migrations (11.20.3, subject to the D6 gates) and Hibernate `validate` in the `mysql` profile, least-privilege accounts, opt-in MySQL integration tests |
| M3 | Create, edit and delete records. (Private PDF upload and download through ownership-checked controllers, `RecordDocument`, was brought forward: D8.) Future requirements recorded in the M4 review: an aggregate per-patient storage quota, and ending open sessions when an account is disabled (together with any account-disabling feature). |
| M4 | Controlled sharing (time-limited, revocable) and an optional emergency QR code (ZXing, only if it's actually useful) |
| M5 | Presentation polish, ED pitch material and final documentation |

---

## 12. Resolved decisions

| ID | Decision (approved 2026-10-10) |
|---|---|
| D1 | Admin bootstrap only when both `MEDIVAULT_ADMIN_EMAIL` and `MEDIVAULT_ADMIN_PASSWORD` are set and valid. It stores only a hash, logs no credentials, and never overwrites an existing account (section 5). |
| D2 | A read-only admin user list with no medical content. A patient gets 403 on admin routes, an admin gets 403 on patient routes, and anonymous users are sent to login. |
| D3 | Opt-in demo records: 3 fictional records per patient registered while **both `demo` and `local-h2`** are active, at most once per account (section 6, hardened 2026-10-10). |
| D4 | "An account with this email already exists." Email enumeration is documented as a known limitation (section 5). |
| D5 | Option A: H2 changed to `runtime` scope for an explicit `local-h2` profile, protected by the H2 startup guard. Tests stay isolated on H2, and MySQL remains the intended database (section 6). |

**Approved and implemented 2026-10-11** (wording checked against the implementation in the M4 review):

| ID | Decision |
|---|---|
| D7 | A separate `local-file` profile for persistent local development without MySQL: file-backed H2 under one configurable storage root (`MEDIVAULT_STORAGE_ROOT`, default `%LOCALAPPDATA%\MediVault`), Flyway H2 migrations + Hibernate `validate`, guarded like `mysql`. `local-h2` stays the disposable in-memory profile; `test` stays in-memory. No new dependency (H2 support is part of `flyway-core`). |
| D8 | PDF documents: `RecordDocument` metadata in the database; the PDF bytes stored via Java NIO (no new dependency) under a random UUID name, per profile: `local-file` uses `<root>\records\<uuid>`, `local-h2` uses `%TEMP%\medivault-local-h2\records\<uuid>`, and `mysql` uses `<root>\mysql-records\<uuid>` (`<root>` = `MEDIVAULT_STORAGE_ROOT`, otherwise `%LOCALAPPDATA%\MediVault`). 10 MB limit per file, extension + declared type + content checks, owner-scoped upload and download under `/records/{id}/documents`, downloads as attachments. A MySQL `V2__record_documents.sql` was added so the `mysql` profile's `validate` keeps working. **Outstanding:** the MySQL migration and the `mysql` document storage have not been verified against MySQL yet. |

*D8 wording corrected in the pre-commit review (2026-10-11) to describe storage per profile; the previous text named `<root>\records\<uuid>`, which is true for `local-file` only.*
