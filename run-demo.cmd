@echo off
REM ---------------------------------------------------------------------------
REM MediVault one-command launcher (Windows). Run from a terminal:
REM
REM   run-demo.cmd              disposable demo  (profiles local-h2,demo; data is forgotten on stop)
REM   run-demo.cmd persistent   persistent local (profiles local-file,demo; data kept in %LOCALAPPDATA%\MediVault)
REM
REM It builds the app, creates the document encryption key once with MediVault's own
REM "document-key" tool (owner-only, outside the storage folder, never overwritten), and starts
REM the server on http://localhost:8080. Stop it with Ctrl+C. Fictional data only.
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"

set "PROFILES="
if "%~1"=="" set "PROFILES=local-h2,demo"
if /i "%~1"=="demo" set "PROFILES=local-h2,demo"
if /i "%~1"=="persistent" set "PROFILES=local-file,demo"
if not defined PROFILES (
    echo Usage: run-demo.cmd [demo^|persistent]
    exit /b 2
)

where java >nul 2>&1
if errorlevel 1 (
    echo Java was not found. Install JDK 17 or newer and set JAVA_HOME, then try again.
    exit /b 1
)

netstat -ano | findstr /r /c:":8080 .*LISTENING" >nul
if not errorlevel 1 (
    echo Port 8080 is already in use. Stop the other program, or the MediVault already running, first.
    exit /b 1
)

set "JAR=target\medivault-0.0.1-SNAPSHOT.jar"

echo [1/3] Building MediVault (tests are skipped here; run "mvnw.cmd test" to run them^)...
call "%~dp0mvnw.cmd" -q package -DskipTests
if errorlevel 1 (
    echo The build failed. See the messages above.
    exit /b 1
)

if not defined MEDIVAULT_DOCUMENT_KEY_FILE set "MEDIVAULT_DOCUMENT_KEY_FILE=%USERPROFILE%\MediVault-keys\document-key.json"
if exist "%MEDIVAULT_DOCUMENT_KEY_FILE%" (
    echo [2/3] Using the existing document encryption key.
) else (
    echo [2/3] Creating the document encryption key ^(once; kept outside the data folder^)...
    for %%F in ("%MEDIVAULT_DOCUMENT_KEY_FILE%") do if not exist "%%~dpF" mkdir "%%~dpF"
    java -jar "%JAR%" document-key generate "%MEDIVAULT_DOCUMENT_KEY_FILE%"
    if errorlevel 1 (
        echo The key file could not be created. See the message above.
        exit /b 1
    )
)

echo [3/3] Starting MediVault with profiles %PROFILES% ...
echo       When the log shows "Tomcat started on port 8080", open http://localhost:8080
echo       Register a patient: you get 3 fictional sample records. Stop with Ctrl+C.
echo.
java -jar "%JAR%" --spring.profiles.active=%PROFILES%
