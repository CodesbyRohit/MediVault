# MediVault

A personal medical records vault built by **Rohit Srivastava** as a Java + Entrepreneurship Development (ED) college mini-project.

> **Status: Milestone 1.** Accounts, secure login, a patient dashboard, an owner-only records list and a
> read-only admin user list are implemented. Adding records and uploading PDFs are **not** implemented yet.
> Requirements: [`docs/milestone-1-requirements.md`](docs/milestone-1-requirements.md).
>
> ⚠️ Use **fictional data only**. MediVault must never contain real patient information.

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

Tests always use their own in-memory H2 database. They ignore `DB_*`, `SPRING_DATASOURCE_*` and
`MEDIVAULT_ADMIN_*` environment variables.

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

## Run against MySQL 8.x

> ⚠️ **Use an isolated MySQL 8.x instance on an explicitly chosen port.** On the author's machine, port **3306**
> belongs to an existing **MySQL 5.5** service that must not be touched. MediVault must never be pointed at it:
> MySQL 5.5 is not supported by Spring Boot 3, and `ddl-auto=update` could change its schema.

Database settings are read from environment variables. **Never commit real credentials.**

| Variable       | Default                                  | Purpose                  |
|----------------|------------------------------------------|--------------------------|
| `DB_URL`       | *(none, **required**)*                   | JDBC URL of the MySQL 8.x instance |
| `DB_USERNAME`  | `medivault`                              | Database user            |
| `DB_PASSWORD`  | *(empty)*                                | Database password        |
| `JPA_DDL_AUTO` | `update`                                 | Hibernate schema mode    |

`DB_URL` has **no default**. If it isn't set and no `local-h2` profile is active, the app refuses to start
(`Could not resolve placeholder 'DB_URL'`) before opening any database connection, so it can never fall back to
another server by accident.

Example (PowerShell, current session only). Replace `<mysql8-port>` with the port of your isolated MySQL 8.x
instance, **not** 3306 on this machine:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:<mysql8-port>/medivault"
$env:DB_USERNAME = "medivault"
$env:DB_PASSWORD = "<your-password>"
.\mvnw.cmd spring-boot:run
```

> **Note:** the MySQL 8.x setup has **not been tested yet**. It is planned for milestone M3, which will also
> introduce Flyway migrations and make Hibernate `validate` the default instead of `update`.

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
Get-ChildItem Env: | Where-Object Name -Match '^(DB_|JPA_DDL_AUTO|SPRING_DATASOURCE_|MEDIVAULT_)' | Select-Object Name
```

To clear them from the current PowerShell session:

```powershell
Remove-Item Env:DB_URL, Env:DB_USERNAME, Env:DB_PASSWORD, Env:JPA_DDL_AUTO, Env:MEDIVAULT_ADMIN_EMAIL, Env:MEDIVAULT_ADMIN_PASSWORD -ErrorAction SilentlyContinue
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
  config/     H2 startup guard (refuses H2 outside "local-h2"/tests)
  demo/       optional fictional demo records ("local-h2" + "demo" profiles)
  record/     medical records: entity, owner-scoped queries, dashboard and record pages
  security/   Spring Security configuration and database-backed login
  user/       accounts, registration, password policy
  web/        landing page, login and registration pages
src/main/resources/
  application.properties            MySQL (default) configuration
  application-local-h2.properties   "local-h2" profile
  templates/                        Thymeleaf pages (layout.html is the shared layout)
  static/                           CSS and images
src/test/                           H2-backed integration and unit tests
docs/                               requirements
```
