@echo off
REM ===========================================================================
REM  CredVaultDemo standalone-server harness (mqsisetdbparms path)
REM  Lifecycle: package -> create work dir -> configure port -> store credential
REM             via mqsisetdbparms userdefined:: -> deploy -> start ->
REM             verify listener -> drive flow -> verify -> stop
REM  Run via run-test-with-ace-env.bat (it sources mqsiprofile first).
REM
REM  KEY POINT proven by testing: the resource MUST use the 'userdefined::'
REM  prefix. A bare resource name is stored as type 'odbc' and the runtime
REM  refuses it from user Java code (BIP9544E) unless you also add that type to
REM  userRetrievableCredentialTypes in server.conf.yaml.
REM ===========================================================================
setlocal enabledelayedexpansion

set "WORKSPACE=%USERPROFILE%\IBM\ACET13\workspace"
set "WORK_DIR=D:\tmp\credvault-test\TEST_SERVER_CRED"
set "SERVER_NAME=TEST_SERVER_CRED"
set "BAR=D:\tmp\CredVaultDemo.bar"
set "HTTP_PORT=7799"
set "CRED_NAME=blogOAuthCred"
set "ENDPOINT=/getcred"

REM -- Non-secret test values, only to prove round-trip retrieval.
set "CRED_USERNAME=svc_blog_user"
set "CRED_PASSWORD=p4ss-WORD"
set "CRED_CLIENT_ID=blog-client-123"
set "CRED_CLIENT_SECRET=s3cr3t-CLIENT"

echo [STEP] Packaging BAR
if exist "%BAR%" del /q "%BAR%"
ibmint package --input-path "%WORKSPACE%" --project CredVaultDemo --project CredVaultDemoJava --output-bar-file "%BAR%" --java-version 17
if errorlevel 1 ( echo [ERROR] package failed & exit /b 1 )
echo [OK] BAR built

echo [STEP] (Re)creating work directory
if exist "%WORK_DIR%" rmdir /s /q "%WORK_DIR%"
call mqsicreateworkdir "%WORK_DIR%"
if errorlevel 1 ( echo [ERROR] mqsicreateworkdir failed & exit /b 1 )

echo [STEP] Setting HTTP listener port in server.conf.yaml
>> "%WORK_DIR%\overrides\server.conf.yaml" echo.
>> "%WORK_DIR%\overrides\server.conf.yaml" echo ResourceManagers:
>> "%WORK_DIR%\overrides\server.conf.yaml" echo   HTTPConnector:
>> "%WORK_DIR%\overrides\server.conf.yaml" echo     ListenerPort: %HTTP_PORT%
echo [OK] server.conf.yaml updated

echo [STEP] Storing credential via mqsisetdbparms (userdefined:: prefix is required)
mqsisetdbparms --work-dir "%WORK_DIR%" --resource userdefined::%CRED_NAME% --user %CRED_USERNAME% --password %CRED_PASSWORD% --client-identity %CRED_CLIENT_ID% --client-secret %CRED_CLIENT_SECRET%
if errorlevel 1 ( echo [ERROR] mqsisetdbparms failed & exit /b 1 )
echo [OK] credential stored

echo [STEP] Deploying application BAR
ibmint deploy --input-bar-file "%BAR%" --output-work-directory "%WORK_DIR%"
if errorlevel 1 ( echo [ERROR] deploy failed & exit /b 1 )
echo [OK] deployed

echo [STEP] Cleaning stale lock
if exist "%WORK_DIR%\config\.lock" del /q "%WORK_DIR%\config\.lock"

echo [STEP] Starting integration server
start "%SERVER_NAME%" cmd /c "IntegrationServer --work-dir ""%WORK_DIR%"" --name %SERVER_NAME% > ""%WORK_DIR%\server.log"" 2>&1"

echo [STEP] Waiting for BIP1991I (server ready)
set /a tries=0
:waitloop
ping 127.0.0.1 -n 3 >nul
findstr /c:"BIP1991I" "%WORK_DIR%\server.log" >nul 2>&1
if not errorlevel 1 goto started
set /a tries+=1
if %tries% geq 40 ( echo [ERROR] server did not become ready & type "%WORK_DIR%\server.log" & goto teardown )
goto waitloop
:started
echo [OK] server ready

echo [STEP] Verifying HTTP listener on %HTTP_PORT%
netstat -an | findstr ":%HTTP_PORT% " | findstr LISTENING >nul
if errorlevel 1 ( echo [ERROR] HTTP listener not up & goto teardown )
echo [OK] listening on %HTTP_PORT%

echo [STEP] Driving flow POST %ENDPOINT%
curl -s -o "%WORK_DIR%\response.json" -w "HTTP_STATUS=%%{http_code}\n" -X POST "http://localhost:%HTTP_PORT%%ENDPOINT%"
echo --- response body ---
type "%WORK_DIR%\response.json"
echo.

echo [STEP] Verifying response
findstr /c:"\"retrieved\":true" "%WORK_DIR%\response.json" >nul
if errorlevel 1 ( echo [ERROR] retrieved flag not true & goto teardown )
findstr /c:"%CRED_CLIENT_ID%" "%WORK_DIR%\response.json" >nul
if errorlevel 1 ( echo [ERROR] clientId not echoed back & goto teardown )
echo [OK] credential retrieved and verified

:teardown
echo [STEP] Stopping server
taskkill /F /FI "WINDOWTITLE eq %SERVER_NAME%*" >nul 2>&1
echo [DONE]
endlocal
