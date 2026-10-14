# MediVault

A personal medical records vault built by **Rohit Srivastava** as a Java + Entrepreneurship Development (ED) college mini-project.

> **Status: Milestone 1 complete; persistent local storage and private PDF documents implemented; record
> management (create, edit, delete, search) implemented; Milestone 2 (MySQL 8.4 + Flyway) in progress.** Accounts,
> secure login, a patient dashboard, owner-only records with search and filters, PDF upload, download and deletion,
> and a read-only admin user list are implemented. MediVault runs **without any MySQL server**, either disposably in
> memory (`local-h2`) or **persistently on this computer** (`local-file`). The MySQL 8.4 foundation is in place in
> code, but **MySQL 8.4 integration has not been verified yet**. Requirements:
> [`docs/milestone-1-requirements.md`](docs/milestone-1-requirements.md).
>
> ⚠️ **Student demo project, not production-ready.** Use **fictional data and fictional PDFs only**. MediVault must
> never contain real patient information. Stored **PDFs are encrypted** with a key file that you create and keep
> (see [Document encryption key](#document-encryption-key)); the **database is not encrypted**, so record titles and
> notes are plain data on disk. This is encryption at rest on the server, **not end-to-end encryption**.

## Quick start: one command (Windows)

Prerequisites: **JDK 17 or newer** with `JAVA_HOME` set. Nothing else: no MySQL, no Maven install.

```powershell
cd <the MediVault project folder>
.\run-demo.cmd               # disposable demo: fresh every start (profiles local-h2,demo)
.\run-demo.cmd persistent    # data survives restarts, in %LOCALAPPDATA%\MediVault (profiles local-file,demo)
```

In Command Prompt, type `run-demo.cmd` without the `.\`. When the log shows `Tomcat started on port 8080`, open
http://localhost:8080, register a patient (you get 3 fictional sample records), and attach a fictional PDF to a
record. Stop the app with `Ctrl+C`.

`run-demo.cmd` builds the app (tests skipped; run `.\mvnw.cmd test` for them). On the first run only, it creates
the document encryption key with MediVault's own `document-key` tool in
`%USERPROFILE%\MediVault-keys\document-key.json`, unless `MEDIVAULT_DOCUMENT_KEY_FILE` already names a key file.
It then starts the server. It never overwrites a key file. If a start is refused, MediVault prints a short
"APPLICATION FAILED TO START" report saying which safety check failed.

## Quick start: manual steps

Maven is not needed: the included Maven Wrapper downloads it (checksum-verified) on first use.

```powershell
cd <the MediVault project folder>

# 0) Once: create the document encryption key OUTSIDE the project folder and outside %LOCALAPPDATA%\MediVault,
#    then point MediVault at it. Back the file up separately from your data (see "Document encryption key").
.\mvnw.cmd -q package -DskipTests
java -jar target\medivault-0.0.1-SNAPSHOT.jar document-key generate "$env:USERPROFILE\MediVault-keys\document-key.json"
$env:MEDIVAULT_DOCUMENT_KEY_FILE = "$env:USERPROFILE\MediVault-keys\document-key.json"

# A) Persistent local mode: data and PDFs survive restarts (stored in %LOCALAPPDATA%\MediVault)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-file,demo"

# B) Disposable demo mode: everything is forgotten when the app stops
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-h2,demo"

# Open http://localhost:8080 on this computer. Stop the app with Ctrl+C.
# Register a patient, log in, open one of the 3 fictional sample records, and attach a fictional PDF.
# Leave out ",demo" to start without sample records.

# Run the tests (in-memory H2, temporary folders and a throwaway key under target\; no MySQL,
# never touches your local data or your real key)
.\mvnw.cmd test
```

| | `local-file` (persistent) | `local-h2` (disposable) |
|---|---|---|
| Accounts and records | File-backed H2 database in `<root>\data\` | In memory; lost when the app stops |
| Uploaded PDFs | `<root>\records\`, **encrypted**, under random UUID names | A scratch folder in your temp directory (`%TEMP%\medivault-local-h2\records`); their database rows are lost on stop, so the files become disposable leftovers |
| Schema | Flyway migrations (`db/migration/h2`) + Hibernate `validate`; never dropped | Hibernate `create-drop` |
| `<root>` | `MEDIVAULT_STORAGE_ROOT`, otherwise `%LOCALAPPDATA%\MediVault` | n/a |

Both modes listen on `127.0.0.1:8080` only, so other computers can't reach them, and the DevTools LiveReload
server and the H2 web console are off. Both use the same Spring Security login, BCrypt hashing and authorization
rules as everywhere else. An admin account is optional: see [Admin account](#admin-account-optional). The `mysql`
profile, its migrations and its safety guard are kept and simply inactive in the local modes. See
[Run against MySQL 8.4](#run-against-mysql-84-mysql-profile-milestone-2).

**Data from `local-h2` is never migrated:** the in-memory database is gone when that app stops, so `local-file`
always starts with its own, initially empty database. Nothing is copied between the modes.

## Features

- Public landing page, with one shared layout that adapts from phone to desktop
- Patient self-registration with server-side validation and field-level error messages
- Passwords stored only as BCrypt hashes. Policy: 8–64 characters, at least one letter and one number, and at
  most 72 bytes (BCrypt's input limit; accented letters and emoji use 2–4 bytes each).
- Login and logout with Spring Security (email + password, CSRF-protected, logout by POST only)
- Patient dashboard built only from the patient's own data: record and document counts, storage used, records by
  type, latest records and recent uploads.
- **Owner-only medical records:** create, view, edit and delete, with server-side validation (title 3–150
  characters, a type, a date that is not in the future and not before 1900, optional provider and notes). Deleting
  asks for confirmation on a separate page, so it also works without JavaScript. An edit made from an outdated copy
  (for example a second browser tab) is refused instead of overwriting newer changes. Another patient's record
  returns 404 on every route.
- **Search and filters** on the records page: text search in title, provider and notes, filters by type, provider and
  date range, four sort orders and pages of 20. Searches are always limited to the patient's own records.
- **PDF documents on records:** a patient can attach PDFs (up to 10 MB each, 100 MB in total per patient) to their own
  record, download them again and delete them. Only the owner can list, download or delete them; another patient
  gets 404 and an admin gets 403.
- Read-only admin user list. Admins cannot see medical record content or documents.
- Optional admin bootstrap from environment variables, plus optional fictional demo records
- Persistent local storage (`local-file`) or a disposable in-memory demo (`local-h2`), both without MySQL

## Tech stack

- Java 17 (bytecode target)
- Spring Boot 3.5 (Spring MVC, Spring Security, Spring Data JPA, Bean Validation)
- Thymeleaf, HTML and CSS (no JavaScript or frontend framework)
- H2 for local development: file-backed with Flyway (`local-file`) or in-memory (`local-h2` and tests).
  MySQL 8.x via the `mysql` profile (not verified yet).
- Java NIO file storage for PDFs, encrypted with Google Tink 1.23.0 (`StreamingAead`, AES256_GCM_HKDF_1MB)
- Maven, through the included Maven Wrapper (no global install needed)
- JUnit 5, Spring Boot Test, Spring Security Test

## Prerequisites

- JDK 17 or newer, with `JAVA_HOME` set (verified with JDK 21 on Windows 11)
- MySQL 8.x, needed only for the `mysql` profile. Tests, `local-file` and `local-h2` don't need it.

## Build and test

```powershell
.\mvnw.cmd -v        # first run downloads Maven 3.9.16 (checksum-verified) into ~/.m2
.\mvnw.cmd test      # runs every test against in-memory H2; no MySQL required
```

On macOS, Linux or Git Bash, use `./mvnw` instead of `.\mvnw.cmd`.

The regular tests always use their own in-memory H2 database. They ignore `DB_*`, `SPRING_DATASOURCE_*` and
`MEDIVAULT_ADMIN_*` environment variables. Test uploads go to `target\test-storage`, and the `local-file` tests use
JUnit temporary folders; a test checks that `%LOCALAPPDATA%\MediVault` is unchanged afterwards.

The MySQL 8.4 integration tests (`src/test/java/com/medivault/mysql`) are **opt-in**: they are reported as
*skipped* unless `MEDIVAULT_IT_DB_URL` is set to the approved `jdbc:mysql://127.0.0.1:<port>/medivault_it?...`
shape (never port 3306), together with the `MEDIVAULT_IT_DB_*` account variables. A skipped run proves nothing
about MySQL.

## Persistent local mode (`local-file` profile)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-file"        # or "local-file,demo"
```

Stop with `Ctrl+C` and start again with the same command: accounts, records and PDFs are still there.

### Where data is stored

```
%LOCALAPPDATA%\MediVault\          (the storage root; usually C:\Users\<you>\AppData\Local\MediVault)
  data\medivault.mv.db             H2 database: accounts, BCrypt hashes, roles, records, document metadata
  records\<uuid>                   uploaded PDFs, one file per document, named only by a random UUID
```

- The folders are created on first start. The database **schema** is created and evolved only by Flyway's
  versioned migrations in `src/main/resources/db/migration/h2`; Hibernate only validates it. Nothing is dropped or
  recreated at startup, and Flyway `clean` is disabled.
- The database stores only document **metadata**: the display filename, the UUID storage key, the verified media
  type, the size, a SHA-256 checksum and the upload time. The PDF bytes are only in `records\`.
- Everything is outside the project folder, the packaged JAR and every static-resource location, so no PDF can be
  reached by URL. Downloads always go through an authenticated, ownership-checked controller.
- `%LOCALAPPDATA%` is private to your Windows account. **PDFs in `records\` are encrypted**, so a copy of that
  folder is unreadable without the key file. **The H2 database is not encrypted**: anyone who can read
  `data\medivault.mv.db` (anyone who can sign in as you, or an administrator of this computer) can read accounts,
  record titles, providers and notes. Use fictional data only.

### Using a different storage root

Set `MEDIVAULT_STORAGE_ROOT` to an **absolute** folder before starting, for example for this session only:

```powershell
$env:MEDIVAULT_STORAGE_ROOT = "D:\MediVaultData"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-file"
```

The `data` and `records` folders are then created inside it. The startup log never prints the path (it contains
your Windows user name), so check the folder in File Explorer.

### Safety rules for `local-file` (checked before any connection)

`DatabaseSafetyGuard` refuses to start, with a message that never contains a path or URL, unless:
- the database is exactly a `jdbc:h2:file:` file directly inside `<root>\data`, and PDFs go to `<root>\records`
  (a `SPRING_DATASOURCE_URL` or other override pointing anywhere else is refused);
- the storage root is an absolute path, and existing `root`, `data` and `records` entries are real folders, not
  files, symbolic links or junctions;
- only allow-listed H2 settings are used (`MODE`, `DATABASE_TO_LOWER`, `DB_CLOSE_ON_EXIT`; never `INIT`,
  `AUTO_SERVER`, `USER`, `PASSWORD` and the like);
- Hibernate only validates, Flyway is enabled without `clean` or `baseline-on-migrate`, and no other schema
  generation or SQL init script is configured;
- `local-file` is not combined with `local-h2`, `test` or `mysql`;
- migrations come only from the packaged `classpath:db/migration/{vendor}` scripts.

File-backed H2 is accepted **only** with `local-file`: the `test` and `local-h2` profiles stay in-memory.

**In every profile** (including `local-h2`, `test` and `mysql`), only `spring.datasource.url` may choose the
database. JNDI and other pool types are refused, and `spring.datasource.hikari.*` and `spring.flyway.*` settings
are allow-listed:
- **Pool:** only sizing and timeouts (`maximum-pool-size`, `minimum-idle`, `connection-timeout`, `idle-timeout`,
  `max-lifetime`, `keepalive-time`, `validation-timeout`, `leak-detection-threshold`,
  `initialization-fail-timeout`, `pool-name`, `register-mbeans`, `auto-commit`).
- **Flyway:** only `enabled`, `locations`, `user`, `password`, `baseline-on-migrate`, `clean-disabled`,
  `validate-on-migrate` and `fail-on-missing-locations`, plus a checked `url` with `mysql` only.

Anything else (for example driver properties, connection SQL, another data source class or account, or Flyway
`init-sqls`) is refused at startup, because it could open another database or run SQL that no rule has checked.
This also covers the matching environment variables, such as `SPRING_DATASOURCE_HIKARI_*` and `SPRING_FLYWAY_*`.

### Backup and restore

The database and the `records` folder belong together (each document row points to one file), so always copy them
**together, with the app stopped**:

```powershell
# Back up (app stopped)
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
Copy-Item -Recurse "$env:LOCALAPPDATA\MediVault" "D:\Backups\MediVault-$stamp"

# Restore (app stopped): move the current folder aside first, never delete it
Rename-Item "$env:LOCALAPPDATA\MediVault" "MediVault-before-restore-$stamp"
Copy-Item -Recurse "D:\Backups\MediVault-<stamp>" "$env:LOCALAPPDATA\MediVault"
```

Copying while the app runs can produce a database and a `records` folder that don't match.

**The document key is not part of this backup, on purpose.** Back up the key file separately (see below). A data
backup without the key cannot be read, and the key alone contains no documents.

### Document encryption key

Every PDF that MediVault stores is encrypted with **Google Tink `StreamingAead` (AES256_GCM_HKDF_1MB)**: AES-256-GCM
in authenticated 1 MiB segments, with a fresh per-file key derived through HKDF-SHA256 from your document key.

**What the encryption guarantees:**
- A copy of the `records` folder (a backup, a synced folder, a copied disk) is unreadable without the key file.
- A changed, truncated, reordered, swapped or substituted file is detected and **never served**. A plaintext file
  put in place of an encrypted one is refused too.
- Every download is first read completely and checked (decrypted, authenticated, size and SHA-256 compared with
  the database) before the first byte is sent.

**What it does not guarantee:** the server holds the key and decrypts files to serve them, so this is **not
end-to-end encryption**. Anyone who can use your Windows account while MediVault runs, or read both the key file
and the data, can read the PDFs. The database itself is **not** encrypted.

**The key file** (`MEDIVAULT_DOCUMENT_KEY_FILE`):
- **Required in every profile**, including `local-h2`. Without a valid key file MediVault **refuses to start**, and
  it **never creates a key by itself**.
- **Format:** a Tink JSON keyset written by the `document-key` tool. **It is not a raw AES key**: raw keys, base64
  strings and other key types are refused.
- **Startup checks.** The file must be:
  - an absolute path to a regular file (not a link or folder), at most 64 KiB;
  - **outside the storage root** (`%LOCALAPPDATA%\MediVault` or `MEDIVAULT_STORAGE_ROOT`), so a data backup never
    contains its key;
  - **readable only by you**. On Windows, only your account, SYSTEM and Administrators may have read access; any
    other entry (Everyone, Users, Authenticated Users, other accounts or groups) is refused. On a non-English
    Windows the SYSTEM and Administrators names differ, so those entries are refused too; files written by the
    tool always pass.
- Startup messages and logs never show the path or the key, only the key id.

**Commands** (run with the app stopped; `java -jar target\medivault-0.0.1-SNAPSHOT.jar`):

| Command | What it does |
|---|---|
| `document-key generate <path>` | Creates a new key file, readable only by you. **Never overwrites** an existing file. Prints only the key id. |
| `document-key rotate <path>` | Adds a new primary key and keeps the old ones, so existing PDFs stay readable. The previous file is kept as `<name>.bak-<timestamp>`. New uploads use the new key. |
| `document-key inspect <path>` | Lists key ids, status and which key is primary. Never prints key material. |

**Backup and recovery:**
- Copy the key file to a separate, safe place (for example an offline USB stick), not next to your data backups.
  Write down its key id.
- **If the key file is lost, every encrypted PDF is permanently unreadable.** There is no recovery in MediVault, by
  design. The records and document names remain, but the files cannot be opened.
- After `rotate`, back up the new key file too. Keep old backups until no document uses the old key.

**Documents uploaded before encryption** (an existing `local-file` folder from an earlier version) stay readable.
They are marked as plain in the database and are checked against their stored SHA-256 on every download. Converting
them to encrypted files is a separate, explicit step that is **not implemented yet**; until then they remain
plaintext on disk.

### Resetting the fictional demo data

There is deliberately **no automatic reset**: MediVault never deletes its database or PDFs. To start fresh, stop
the app and move the folder aside (keep it until you are sure):

```powershell
Rename-Item "$env:LOCALAPPDATA\MediVault" "MediVault-old-$(Get-Date -Format yyyyMMdd-HHmmss)"
```

The next start creates an empty database. With `,demo`, each patient who registers gets the 3 fictional sample
records again (once per account; restarts never add more, and existing accounts are never changed).

### Interrupted uploads and leftover files

Each upload is checked and encrypted in one streaming pass into a temporary `upload-*.part` file in `records\`. No
plaintext is written to this folder. The file is then forced to disk, read back and verified, and renamed to its
UUID name (never replacing an existing file). Only then is the metadata row saved. If saving the row fails, that
one new file is deleted again. If the app is killed in the middle, the following can remain; none of it is ever
served:
- `upload-*.part` files: unfinished uploads, encrypted and incomplete. Startup logs how many exist. They can be
  deleted while the app is stopped.
- A UUID file without a database row (only if the app dies between the rename and the commit). It is never shown
  or served. It is left alone rather than deleted automatically.

### Deleting documents and records

Deleting a document (or a record, which deletes all its documents) removes the database rows first and the PDF files
only **after** that deletion has committed. If the deletion fails, nothing changes. If a file cannot be removed
afterwards (for example because another program has it open), the document is still deleted and the file stays behind
unreferenced: it is never shown or served, and a warning with the number of such files is logged.

### Document storage check

At every `local-file` and `mysql` start, MediVault compares the document rows with the files in the `records` folder
and logs **counts only** (`medivault.documents.reconcile-on-startup`, on by default; off for `local-h2`, whose rows
vanish on every stop):
- **unreferenced files**: files without a row, from an interrupted upload or a file that could not be removed after a
  deletion. They are never served. MediVault never deletes them itself. With the app stopped and a backup made, you
  may move them out of the `records` folder.
- **rows whose file is missing**: these documents cannot be downloaded (a generic error page is shown). Restore the
  `records` folder from the backup that belongs to the same database.

H2 writes committed changes to disk shortly after each commit (its documented write delay). Stop the app with
`Ctrl+C`; if the process is killed or the computer loses power, the most recent changes can be lost.

## Disposable local mode (`local-h2` profile)

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
- Uploaded PDFs go to `%TEMP%\medivault-local-h2\records`. Their database rows vanish when the app stops, so these
  fictional files are disposable leftovers and can be deleted at any time while the app is stopped.

### Optional: fictional demo records (`demo` + `local-file` or `local-h2`)

Add the `demo` profile **together with `local-file` or `local-h2`** to give each **newly registered** patient 3
clearly labelled fictional sample records:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-file,demo"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local-h2,demo"
```

- **Off by default.** `demo` alone (or with `mysql`) creates **no** records and logs a warning saying so. The
  startup guard pins both local profiles to H2, so demo records can never be written to MySQL.
- A warning is logged at startup while demo mode is active.
- Each account is seeded at most once, at registration. Accounts that already have records are skipped, so
  restarting `local-file,demo` never duplicates samples. No existing account, password or role is changed.
- Demo mode creates no PDF files. To try uploads, use any fictional PDF (for example, print a page saying
  "FICTIONAL SAMPLE" with *Microsoft Print to PDF*).
- **To disable it,** start without `demo`.

### PDF upload rules

- A logged-in patient attaches PDFs on their own record's page. The owner is always taken from the login session,
  never from the form or URL.
- Accepted only when **all** of these hold: the filename ends in `.pdf`, the browser declares `application/pdf`,
  the content starts with a PDF header (`%PDF-1.x` / `%PDF-2.x`) and contains the `%%EOF` end marker in its last
  kilobyte, it is not empty, and it is at most **10 MB** (`medivault.documents.max-size`). A renamed program or web
  page is therefore refused even if it claims to be a PDF.
- This is a structural check only: MediVault does not parse, render, scan or execute PDFs. Downloads are sent with
  `Content-Disposition: attachment`, so the browser saves the file instead of opening it inside MediVault.
- The original filename is shown for display only (path segments and unsafe characters are removed); it is never
  used to build a file path.
- Errors are shown as a plain message on the record page. Files over the limit are refused by the server before
  anything is stored.
- Accepted PDFs are stored encrypted. The SHA-256 checksum and size kept in the database describe the original PDF.
  Every download checks them against the decrypted file. A document that fails the check (damaged, changed,
  swapped, or unreadable with the current key file) is never sent: a generic error page is shown, and a log line
  names only the document id and the reason.

## Hosted demo profile (`railway`, Stage 1)

> **Fictional data only. Not deployed yet:** the deployment, key-management and disk-fill steps are still to be
> approved and documented.

`railway` is a small overlay for running the `local-file` setup on Railway. Start it with exactly
`SPRING_PROFILES_ACTIVE=local-file,railway`, in that order. `local-file` still provides the storage, the encrypted
PDFs and all its safety rules. Set `MEDIVAULT_STORAGE_ROOT` to a folder on the service's persistent volume, and
`MEDIVAULT_DOCUMENT_KEY_FILE` as for any other profile. `railway` only changes how the server is reached:

| Setting | Value | Why |
|---|---|---|
| `server.address` | `0.0.0.0` | Railway connects to the container's network interface (`local-file` alone listens on 127.0.0.1 only) |
| `server.port` | `${PORT:8080}` | Railway passes the port in `PORT` |
| `server.forward-headers-strategy` | `native` | HTTPS ends at Railway's proxy. Tomcat applies its `X-Forwarded-*` headers for internal (private-range) proxy addresses, so the app sees HTTPS: an HSTS header is sent and redirects use `https://` |
| `server.servlet.session.cookie.secure` | `true` | The session cookie is always `Secure` |

`HostedProfileGuard` refuses to start with `railway` if:
- `local-file` is missing or listed after `railway`;
- `demo` is active (a hosted instance never creates sample records);
- either of the last two settings above is changed.

`RailwayProfileTests` checks this with a real server on 127.0.0.1. Whether Railway's own proxy address counts as
internal can only be checked after deployment: the live site must send a `Strict-Transport-Security` header.

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
- With `local-h2`, the admin is created again at each start, because the in-memory database starts empty. With
  `local-file`, it is created once and later starts report that it already exists.

## Checking and clearing environment variables

To see which relevant variables are set (names only, values hidden):

```powershell
Get-ChildItem Env: | Where-Object Name -Match '^(DB_|SPRING_DATASOURCE_|SPRING_JPA_|SPRING_FLYWAY_|MEDIVAULT_)' | Select-Object Name
```

To clear them from the current PowerShell session:

```powershell
Remove-Item Env:DB_URL, Env:DB_USERNAME, Env:DB_PASSWORD, Env:DB_MIGRATION_USERNAME, Env:DB_MIGRATION_PASSWORD, Env:MEDIVAULT_ADMIN_EMAIL, Env:MEDIVAULT_ADMIN_PASSWORD, Env:MEDIVAULT_STORAGE_ROOT -ErrorAction SilentlyContinue
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
- **Encryption at rest covers PDFs only.** Stored PDFs are encrypted (see
  [Document encryption key](#document-encryption-key)). The H2 database file is **not** encrypted and is protected
  only by Windows account permissions: it uses the built-in `sa` account without a password, so anyone who can read
  the file can open it and read accounts, record titles, providers and notes. Database backups are just as readable.
  PDFs uploaded before encryption existed stay plaintext until converted (conversion is not implemented yet).
- **Single computer, single instance.** `local-file` is for one person on one computer. H2 locks the database file
  while the app runs (its default file lock), so don't start a second copy on the same storage root (this case is
  not covered by the tests). There is no replication, sync or automatic backup.
- PDFs are checked structurally, not virus-scanned or sanitised; they may still contain active content that a PDF
  reader would run. Open downloads only with an up-to-date PDF reader. Use fictional documents only.
- **Residual plaintext during uploads:** the servlet container briefly buffers each upload, **unencrypted**, in
  its own temporary folder (inside your Windows temp directory) and deletes it after the request. If the app or the
  computer crashes during an upload, that temporary copy can remain there; it is outside MediVault's control and is
  not reported by the storage check.
- Documents can be deleted but not replaced; upload the corrected PDF and delete the old one.
- **Per-patient storage quota:** each PDF is limited to 10 MB and all of one patient's PDFs together to 100 MB
  (`medivault.documents.quota-per-patient`). Uploads by the same patient are serialised with a database row lock so
  parallel uploads cannot exceed it. The disk can still fill up with many accounts, because registration is open
  and there is no limit on the number of accounts or on total storage.
- **Disabled accounts:** a disabled account cannot log in, but a session that was already open is not ended when
  the account is disabled. This is deferred until account disabling exists (no admin screen can disable accounts
  yet).
- **Logging:** Spring Boot's startup-info lines (which name your Windows user and the project folder) are
  switched off (`spring.main.log-startup-info=false`), so the "Started ... in N seconds" line no longer appears;
  "Tomcat started on port 8080" shows the app is ready. A clean `local-file` run logged no paths, JDBC URLs,
  storage keys, passwords, tokens or stack traces. Spring's own `Resolved [...]` warnings, which can repeat a
  malformed request value or the requested path (logged in every profile by `DefaultHandlerExceptionResolver`, and
  by the other resolvers under DevTools), are switched off. Each handled request error is logged as one sanitised
  line instead, for example
  `Rejected request: GET /records/{id} -> MethodArgumentTypeMismatchException (parameter 'id' must be Long)`
  (`medivault.web.log-rejected-requests`, on by default). Unhandled errors are still logged with their exception.

## Project layout

```
src/main/java/com/medivault/
  admin/      read-only user list, admin bootstrap
  config/     DatabaseSafetyGuard + MySqlUrlPolicy + LocalFileStoragePolicy (database profile, H2, storage
              location and MySQL connection rules, enforced before any connection); HostedProfileGuard
              ("railway" profile rules); StartupRefusalFailureAnalyzer (short report for refused starts)
  demo/       optional fictional demo records ("demo" + "local-file" or "local-h2")
  document/   PDF documents: metadata entity, private encrypted file storage, upload/download controller
  document/crypto/  document key file loading and checks, encrypted container (Tink), document-key tool
  record/     medical records: entity, owner-scoped queries, dashboard and record pages
  security/   Spring Security configuration and database-backed login
  user/       accounts, registration, password policy
  web/        landing page, login and registration pages, sanitised logging of rejected requests
src/main/resources/
  application.properties              shared settings (no datasource; a database profile is required)
  application-local-file.properties   "local-file" profile (file-backed H2 + PDF folder, Flyway + validate)
  application-local-h2.properties     "local-h2" profile (in-memory H2, Flyway off)
  application-mysql.properties        "mysql" profile (MySQL 8.4 via env vars, Flyway + Hibernate validate)
  application-railway.properties      "railway" overlay for the hosted demo ("local-file,railway")
  db/migration/h2/                    Flyway migrations for "local-file" (V1 baseline, V2 record_documents, V3 record version,
                                      V4 document encryption)
  db/migration/mysql/                 Flyway migrations for "mysql" (V1–V4, same content)
  templates/                        Thymeleaf pages (layout.html is the shared layout)
  static/                           CSS and images
src/test/java/com/medivault/        H2-backed integration and unit tests
src/test/java/com/medivault/mysql/  opt-in MySQL 8.4 tests (skipped unless MEDIVAULT_IT_DB_URL is set)
docs/                               requirements
run-demo.cmd                        one-command Windows launcher (build, key on first run, start)
```
