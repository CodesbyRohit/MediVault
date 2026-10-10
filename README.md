# MediVault

A personal medical records vault built as a Java + Entrepreneurship Development (ED) college mini-project.

> **Status:** project skeleton only. Application features have not been implemented yet.

## Tech stack

- Java 17
- Spring Boot 3.5 (Spring MVC, Spring Security, Spring Data JPA, Bean Validation)
- Thymeleaf, HTML, CSS, JavaScript
- MySQL 8.x (runtime), H2 in-memory (tests only)
- Maven (via the included Maven Wrapper, no global install needed)
- JUnit 5 and Spring Boot Test

## Prerequisites

- JDK 17 or newer, with `JAVA_HOME` set (the build targets Java 17 bytecode)
- MySQL 8.x, needed only to run the app. Tests do not need it.

## Configuration

Database settings are read from environment variables. **Never commit real credentials.**

| Variable       | Default                                  | Purpose                  |
|----------------|------------------------------------------|--------------------------|
| `DB_URL`       | `jdbc:mysql://localhost:3306/medivault`  | JDBC URL                 |
| `DB_USERNAME`  | `medivault`                              | Database user            |
| `DB_PASSWORD`  | *(empty)*                                | Database password        |
| `JPA_DDL_AUTO` | `update`                                 | Hibernate schema mode    |

Example (PowerShell, current session only):

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/medivault"
$env:DB_USERNAME = "medivault"
$env:DB_PASSWORD = "<your-password>"
```

To see which of these are set (names only, values hidden):

```powershell
Get-ChildItem Env: | Where-Object Name -Match '^(DB_|JPA_DDL_AUTO|SPRING_DATASOURCE_)' | Select-Object Name
```

To clear them from the current PowerShell session:

```powershell
Remove-Item Env:DB_URL, Env:DB_USERNAME, Env:DB_PASSWORD, Env:JPA_DDL_AUTO -ErrorAction SilentlyContinue
```

If you saved any of them permanently for your Windows user, remove each one like this, then open a new terminal:

```powershell
[Environment]::SetEnvironmentVariable("DB_PASSWORD", $null, "User")
```

Tests ignore all of these variables, including `SPRING_DATASOURCE_*`, and always use in-memory H2.

## Build and test

```powershell
.\mvnw.cmd -v        # first run downloads Maven 3.9.16 (checksum-verified) into ~/.m2
.\mvnw.cmd test      # runs tests against in-memory H2; no MySQL required
```

On macOS/Linux/Git Bash use `./mvnw` instead of `.\mvnw.cmd`.

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

The app then runs at http://localhost:8080.

> **Note:** running the app needs a reachable **MySQL 8.x** database matching the `DB_*` variables above.
> MySQL 5.5 is not supported by Spring Boot 3. Tests do **not** need MySQL; they use in-memory H2.

### Temporary default login

MediVault has no user accounts yet, so Spring Security's development defaults apply:

- Every page requires login. You are redirected to the built-in `/login` page.
- The username is `user`.
- A new random password is generated on **every** start and printed once in the console, on a line beginning
  `Using generated security password:`. Look for that line in your own console output.
- This is for local development only. It will be replaced by real accounts with hashed passwords when
  authentication is implemented. Don't share console logs that contain this line.

## Project layout

```
src/main/java/com/medivault/       application code
src/main/resources/                application.properties, templates/, static/
src/test/java/com/medivault/       tests
src/test/resources/                application-test.properties (H2)
```
