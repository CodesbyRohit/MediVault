# MediVault

A personal medical records vault built by **Rohit Srivastava** as a Java + Entrepreneurship Development (ED) college mini-project.

> **Status: Milestone 1 complete; Milestone 2 (MySQL 8.4 + Flyway) in progress.** Accounts, secure login, a
> patient dashboard, an owner-only records list and a read-only admin user list are implemented. The MySQL 8.4
> foundation (Flyway migration, `mysql` profile, fail-closed connection guard, opt-in MySQL tests) is in place in
> code, but **MySQL 8.4 integration has not been verified yet**. Adding records and uploading PDFs are **not**
> implemented. Requirements: [`docs/milestone-1-requirements.md`](docs/milestone-1-requirements.md).
>
> ⚠️ Use **fictional data only**. MediVault must never contain real patient information.

## Quick start: no MySQL needed (local H2 mode)

MediVault runs locally **without any MySQL server**, using the `local-h2` profile: an in-memory H2 database inside
the application, with the same Spring Security login, BCrypt hashing, authorization rules and server-side record
storage as everywhere else. Only a JDK 17+ is required.

```powershell
cd "D:\Rohit Srivastava\MediVault"

# 1. Start the app (Ctrl+C stops it). Add ",demo" to the profile list for fictional sample records.
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-h2"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-h2,demo"

# 2. Open the app in a browser on this computer:  http://localhost:8080
#    Register a patient account, log in, and (with ",demo") see 3 fictional sample records.

# 3. Run the tests (in-memory H2; no MySQL needed)
.\mvnw.cmd test
```

- **Development only, fictional data only.** Local H2 mode is not a place to store real patients' confidential
  medical information.
- **Data resets:** the H2 database lives in memory, so every account and record disappears when the app stops.
- **Local only:** in this mode the app listens on `127.0.0.1:8080`, so it can't be reached from other computers.
  The DevTools LiveReload server and the H2 web console are off.
- **An admin account** is optional. See [Admin account](#admin-account-optional).
- **MySQL work is kept for later:** the `mysql` profile, the Flyway migration (`db/migration/mysql`) and the MySQL
  safety guard are unchanged and simply inactive in local H2 mode. See
  [Run against MySQL 8.4](#run-against-mysql-84-mysql-profile-milestone-2).

## Features (Milestone 1)

- Public landing page, with one shared layout that adapts from phone to desktop
- Patient self-registration with server-side validation and field-level error messages
- Passwords stored only as BCrypt hashes. Policy: 8–64 characters, at least one letter and one number, and at
  most 72 bytes (BCrypt's input limit; accented letters and emoji use 2–4 bytes each).
- Login and logout with Spring Security (email + password, CSRF-protected, logout by POST only)
- Patient dashboard and **owner-only** medical-record list and detail pages. Another patient's record returns 404.
- Read-only admin user list. Admins cannot see medical record content.
- Optional admin bootstrap from environment variables, plus optional fictional demo records

## Tech stack

- Java 17 (bytecode target)
- Spring Boot 3.5 (Spring MVC, Spring Security, Spring Data JPA, Bean Validation)
- Thymeleaf, HTML and CSS (no JavaScript or frontend framework)
- MySQL 8.x as the application database. H2 in-memory for tests and the `local-h2` demo profile.
- Maven, through the included Maven Wrapper (no global install needed)
- JUnit 5, Spring Boot Test, Spring Security Test

## Prerequisites

- JDK 17 or newer, with `JAVA_HOME` set
- MySQL 8.x, needed only to run against a real database. Tests and the `local-h2` profile don't need it.

## Build and test

```powershell
.\mvnw.cmd -v        # first run downloads Maven 3.9.16 (checksum-verified) into ~/.m2
.\mvnw.cmd test      # runs every test against in-memory H2; no MySQL required
```

On macOS, Linux or Git Bash, use `./mvnw` instead of `.\mvnw.cmd`.

The regular tests always use their own in-memory H2 database. They ignore `DB_*`, `SPRING_DATASOURCE_*` and
`MEDIVAULT_ADMIN_*` environment variables.

The MySQL 8.4 integration tests (`src/test/java/com/medivault/mysql`) are **opt-in**: they are reported as
*skipped* unless `MEDIVAULT_IT_DB_URL` is set to the approved `jdbc:mysql://127.0.0.1:<port>/medivault_it?...`
shape (never port 3306), together with the `MEDIVAULT_IT_DB_*` account variables. A skipped run proves nothing
about MySQL.

## Run locally without MySQL (`local-h2` profile)

**Step 1: make sure no datasource override is set.** Environment variables take precedence over profile
files, so a `SPRING_DATASOURCE_URL` variable would **replace** the in-memory H2 settings. If it points anywhere
other than an in-memory H2 database (for example at MySQL), the app **refuses to start** with this profile rather
than connect to that database. To avoid that, check and clear the variables first. This prints variable names only:

```powershell
Get-ChildItem Env: | Where-Object Name -Match '^SPRING_DATASOURCE_' | Select-Object Name
# If anything is listed, clear it for this session, e.g.:
Remove-Item Env:SPRING_DATASOURCE_URL, Env:SPRING_DATASOURCE_USERNAME, Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
```

**Step 2: start the app.**

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-h2"
```

Then open http://localhost:8080 and stop the app with `Ctrl+C`.

- In PowerShell, keep the quotes around the `-D...` argument.
- The database is **in memory**: every account and record disappears when the app stops, and nothing is written
  to disk. `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` are ignored by this profile.
- The startup log line `HikariPool-1 - Added connection ... url=jdbc:h2:mem:medivault_local` confirms that the
  in-memory database is in use.
- The app listens on `127.0.0.1:8080` only, and the DevTools LiveReload server is disabled. To show the app
  from another device on a trusted network, start it with `"-Dspring-boot.run.arguments=--server.address=0.0.0.0"`;
  still use fictional data only.
- **Safety guards** (checked before the app starts; messages never show the database URL):
  - H2 is only accepted with the `local-h2` (or test) profile. If the app is started without it while pointing
    at H2, or with an empty database URL, it refuses to start with
    "Refusing to start: the configured database is H2...", instead of silently using an in-memory database.
  - `local-h2` is only accepted with its in-memory H2 database (`jdbc:h2:mem:`). If a `SPRING_DATASOURCE_URL`
    override points elsewhere, it refuses to start with
    "Refusing to start: the 'local-h2' profile requires its in-memory H2 database...".

### Optional: fictional demo records (`local-h2` + `demo`)

Add the `demo` profile **together with `local-h2`** to give each **newly registered** patient 3 clearly
labelled fictional sample records:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-h2,demo"
```

- **Off by default**, and only active with **both** profiles. `demo` without `local-h2` creates **no** records
  and logs a warning saying so. With `local-h2`, the startup guard ensures the database is in-memory H2, so
  `local-h2,demo` combined with a MySQL datasource override is rejected at startup and never writes demo records
  to MySQL.
- A warning is logged at startup while demo mode is active.
- Each account is seeded at most once, at registration. Accounts that already have records are skipped.
- **To disable it,** start without `demo`: `"-Dspring-boot.run.profiles=local-h2"`.

## Run against MySQL 8.4 (`mysql` profile, Milestone 2)

> ⚠️ **Use an isolated MySQL 8.4 instance on 127.0.0.1 and an explicitly chosen port.** MediVault refuses to
> connect to port **3306** (the default MySQL port, historically used by a legacy MySQL 5.5 installation on the
> author's machine) at all.
>
> **Not verified, and currently not runnable here:** no MySQL server is installed on the development machine at
> the moment (both MySQL installations were removed on 2026-10-11). This section describes the intended setup;
> use the `local-h2` profile for development until the steps below are completed.

**Remaining MySQL work (Milestone 2), in order. None of these steps has been done yet:**
1. Install or restore an isolated MySQL 8.4 server bound to `127.0.0.1` on a port other than 3306, with
   `require_secure_transport=ON`.
2. Verify the root-password rotation and that plaintext connections are refused (MySQL error 3159).
3. Create `medivault_dev`, `medivault_it` and the four least-privilege accounts (`medivault_{dev,it}_{migrator,app}`),
   all `@127.0.0.1` with `REQUIRE SSL`, then verify their exact grants.
4. Copy the server's public `ca.pem` and build a passwordless PKCS12 truststore for `sslMode=VERIFY_CA`.
5. Run the opt-in MySQL tests against `medivault_it` (they are reported as skipped until then). This also decides
   the enum column type and confirms Flyway 11.20.3 works with MySQL 8.4.
6. Migrate and validate `medivault_dev` independently.

A database profile must always be chosen explicitly. Starting without `local-h2` or `mysql` is refused
("Refusing to start: no database profile is active..."), so the app can never fall back to another server.

With the `mysql` profile, **Flyway** creates and evolves the schema from versioned migrations in
`src/main/resources/db/migration/mysql`, using a separate migration account. Hibernate only **validates** the
schema (`ddl-auto=validate`); it never changes it. Flyway `clean` and `baseline-on-migrate` are disabled.

All settings come from environment variables, with **no defaults**. **Never put passwords in `DB_URL`, files or Git.**

| Variable | Example / rule |
|---|---|
| `DB_URL` | `jdbc:mysql://127.0.0.1:<port>/medivault_dev?sslMode=VERIFY_CA&trustCertificateKeyStoreUrl=file:/<path>&trustCertificateKeyStoreType=PKCS12` |
| `DB_USERNAME` / `DB_PASSWORD` | `medivault_dev_app`: data access only |
| `DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD` | `medivault_dev_migrator`: Flyway schema changes only |

Before any connection is attempted, `DatabaseSafetyGuard` refuses the configuration unless:
- the host is exactly `127.0.0.1` with an explicit port that is **not 3306**, and the database is
  `medivault_dev` or `medivault_it`;
- `sslMode` is `VERIFY_CA` or `VERIFY_IDENTITY` (never `DISABLED`, `PREFERRED` or `REQUIRED`), and only
  allow-listed URL parameters are used: no credentials, `useSSL`, `allowPublicKeyRetrieval` and similar;
- the accounts match the database (`medivault_<env>_app` / `medivault_<env>_migrator`);
- `ddl-auto` is `validate` and Flyway is enabled, with `clean` disabled.

Refusal messages never contain the URL or credentials. Flyway's own URL log line is silenced in this profile.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

**Note on TLS:** the auto-generated MySQL certificate has no Subject Alternative Name, so only `VERIFY_CA`
(certificate-chain verification) works with it; `VERIFY_IDENTITY` would need a reviewed replacement certificate.

## Admin account (optional)

Registration only ever creates **patients**. To get an administrator, set **both** variables before
starting the app. Use your own values; never commit them:

```powershell
$env:MEDIVAULT_ADMIN_EMAIL = "<admin-email>"
$env:MEDIVAULT_ADMIN_PASSWORD = "<admin-password>"   # 8–64 chars, letter + number, max 72 bytes
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-h2"
```

- The admin is created at startup **only if no account with that email exists yet**. An existing account's
  password or role is never changed, and an existing patient is never promoted.
- **Changing `MEDIVAULT_ADMIN_EMAIL` creates an additional admin** on the next start. The previous admin stays
  active, because the bootstrap deliberately never disables, deletes or modifies existing accounts. With MySQL,
  removing an old admin is a manual database task until admin account management exists.
- Only the BCrypt hash is stored. Logs never show the email, password or hash, only the new account's ID.
- If only one variable is set, or a value is invalid, a warning explains what is wrong (without showing the
  value) and no admin is created.
- With `local-h2`, the admin is created again at each start, because the in-memory database starts empty.

## Checking and clearing environment variables

To see which relevant variables are set (names only, values hidden):

```powershell
Get-ChildItem Env: | Where-Object Name -Match '^(DB_|SPRING_DATASOURCE_|SPRING_JPA_|SPRING_FLYWAY_|MEDIVAULT_)' | Select-Object Name
```

To clear them from the current PowerShell session:

```powershell
Remove-Item Env:DB_URL, Env:DB_USERNAME, Env:DB_PASSWORD, Env:DB_MIGRATION_USERNAME, Env:DB_MIGRATION_PASSWORD, Env:MEDIVAULT_ADMIN_EMAIL, Env:MEDIVAULT_ADMIN_PASSWORD -ErrorAction SilentlyContinue
Get-ChildItem Env: | Where-Object Name -Like 'MEDIVAULT_IT_*' | ForEach-Object { Remove-Item "Env:$($_.Name)" }
```

If you saved any of them permanently for your Windows user, remove each one like this, then open a new terminal:

```powershell
[Environment]::SetEnvironmentVariable("DB_PASSWORD", $null, "User")
```

## Security notes and known limitations

- CSRF protection is on, and logout is POST-only. HTTP Basic is off. Any route not explicitly allowed is denied,
  and internal forwards are authorised like direct requests. A strict Content-Security-Policy is sent with every response.
- The session ID changes at login. The session cookie is `HttpOnly` and `SameSite=Lax`.
- **Before the faculty demo**, check manually in a real browser: the 320 px phone layout, keyboard-only navigation,
  and that the browser console shows no Content-Security-Policy errors.
- Failed logins always show the same message: "Invalid email or password".
- **Email enumeration:** registration says "An account with this email already exists." This is fine for a
  coursework demo, but a public healthcare product would need a neutral message, email confirmation and rate limiting.
- There is no login rate-limiting, account lockout, password reset, remember-me or email verification yet.
- There is no HTTPS in local development, so the session cookie is not marked `Secure`.

## Project layout

```
src/main/java/com/medivault/
  admin/      read-only user list, admin bootstrap
  config/     DatabaseSafetyGuard + MySqlUrlPolicy (database profile, H2 and MySQL connection rules,
              enforced before any connection)
  demo/       optional fictional demo records ("local-h2" + "demo" profiles)
  record/     medical records: entity, owner-scoped queries, dashboard and record pages
  security/   Spring Security configuration and database-backed login
  user/       accounts, registration, password policy
  web/        landing page, login and registration pages
src/main/resources/
  application.properties            shared settings (no datasource; a database profile is required)
  application-local-h2.properties   "local-h2" profile (in-memory H2, Flyway off)
  application-mysql.properties      "mysql" profile (MySQL 8.4 via env vars, Flyway + Hibernate validate)
  db/migration/mysql/               Flyway versioned migrations (V1__baseline_schema.sql)
  templates/                        Thymeleaf pages (layout.html is the shared layout)
  static/                           CSS and images
src/test/java/com/medivault/        H2-backed integration and unit tests
src/test/java/com/medivault/mysql/  opt-in MySQL 8.4 tests (skipped unless MEDIVAULT_IT_DB_URL is set)
docs/                               requirements
```
