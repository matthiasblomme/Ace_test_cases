@echo off
setlocal EnableDelayedExpansion

REM ---------------------------------------------------------------------------
REM RCDInvalidCharRepro - standalone integration server harness.
REM
REM Deploys the application into a private work directory, starts a server, and
REM drives all six HTTP paths, capturing the status code, the response body and
REM the BIP codes each one produces.
REM
REM Must be invoked through run-test-with-ace-env.bat, which sources mqsiprofile.
REM Pass -fresh to delete and rebuild the work directory.
REM ---------------------------------------------------------------------------

if not defined ACE_VERSION      set "ACE_VERSION=13.0.8.1"
if not defined SERVER_NAME      set "SERVER_NAME=RCD_TEST"
if not defined BASE_DIR         set "BASE_DIR=C:\temp\rcdrepro"
if not defined HTTP_PORT        set "HTTP_PORT=7801"
if not defined ADMIN_PORT       set "ADMIN_PORT=7601"

set "PROJECT_NAME=RCDInvalidCharRepro"
set "JAVA_PROJECT=RCDInvalidCharReproJava"
set "HARNESS_DIR=%~dp0"
set "PROJECT_DIR=%HARNESS_DIR%..\.."
set "INPUT_DIR=%HARNESS_DIR%..\test-resources\input"
set "RESULTS_DIR=%HARNESS_DIR%results"
set "STAGE_DIR=%BASE_DIR%\Sources"
set "WORK_DIR=%BASE_DIR%\%SERVER_NAME%"
set "CONSOLE_LOG=%BASE_DIR%\%SERVER_NAME%.console.log"

echo.
echo ==========================================================================
echo  RCDInvalidCharRepro harness   ACE %ACE_VERSION%   port %HTTP_PORT%
echo ==========================================================================

where ibmint >nul 2>&1
if errorlevel 1 (
    echo [ERROR] ibmint not on PATH. Run this through run-test-with-ace-env.bat.
    exit /b 1
)

REM --- STEP 1: dependencies -------------------------------------------------
echo [STEP 1] Dependencies
echo [OK]     None. No SupportPac, no third-party jars, no shared-classes.

REM --- STEP 2: provision test environment -----------------------------------
echo [STEP 2] Provision test environment

call :STOP_SERVER

if /I "%~1"=="-fresh" (
    if exist "%WORK_DIR%" (
        echo [INFO]   -fresh given, removing %WORK_DIR%
        rmdir /S /Q "%WORK_DIR%"
    )
)

if not exist "%BASE_DIR%"    mkdir "%BASE_DIR%"
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"

REM Stage the project on its own. --input-path scans a directory for projects,
REM so pointing it at the shared Toolkit workspace would pull in every sibling.
if exist "%STAGE_DIR%" rmdir /S /Q "%STAGE_DIR%"
mkdir "%STAGE_DIR%\%PROJECT_NAME%"
REM robocopy, not xcopy. xcopy /EXCLUDE matches substrings from a pattern file and
REM silently copied nothing here; robocopy takes directory names directly and says
REM what it did. Exit codes 0-7 are success, 8+ is a real failure.
robocopy "%PROJECT_DIR%" "%STAGE_DIR%\%PROJECT_NAME%" /E /XD testing .settings /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 (
    echo [ERROR] Staging copy failed with robocopy code %ERRORLEVEL%.
    exit /b 1
)
if not exist "%STAGE_DIR%\%PROJECT_NAME%\.project" (
    echo [ERROR] Staged tree has no .project - nothing for ibmint to find.
    exit /b 1
)
REM The JavaCompute class lives in a sibling Java project the app references.
robocopy "%PROJECT_DIR%\..\%JAVA_PROJECT%" "%STAGE_DIR%\%JAVA_PROJECT%" /E /XD .settings /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 (
    echo [ERROR] Staging copy of %JAVA_PROJECT% failed.
    exit /b 1
)
if not exist "%STAGE_DIR%\%JAVA_PROJECT%\.project" (
    echo [ERROR] Staged %JAVA_PROJECT% has no .project.
    exit /b 1
)
echo [OK]     Staged %PROJECT_NAME% to %STAGE_DIR%

if not exist "%WORK_DIR%" (
    REM "call" is load-bearing. mqsicreateworkdir is a .cmd script, so invoking it
    REM bare from a .bat transfers control and never comes back: the harness ends
    REM here, having printed "Successful command completion", and looks like a pass.
    REM ibmint and IntegrationServer are real .exe files and do not need it.
    call mqsicreateworkdir "%WORK_DIR%"
    if errorlevel 1 (
        echo [ERROR] mqsicreateworkdir failed.
        exit /b 1
    )
    echo [OK]     Created work directory %WORK_DIR%
) else (
    echo [OK]     Reusing work directory %WORK_DIR%
)

REM A crashed run leaves this behind and the next start blocks on it.
if exist "%WORK_DIR%\config\.lock" (
    echo [INFO]   Removing stale config\.lock
    del /Q "%WORK_DIR%\config\.lock"
)

REM --- STEP 3: shared-classes -----------------------------------------------
echo [STEP 3] shared-classes
echo [OK]     None required.

REM --- STEP 4: deploy -------------------------------------------------------
echo [STEP 4] Deploy
REM No policy projects, so the application is the only thing to deploy.
REM --compile-maps-and-schemas is load-bearing: without it Order.xsd is not
REM compiled and the XMLNSC validation this repro is about never has a model.
ibmint deploy --input-path "%STAGE_DIR%" --output-work-directory "%WORK_DIR%" --project %PROJECT_NAME% --project %JAVA_PROJECT% --compile-maps-and-schemas --java-version 17
if errorlevel 1 (
    echo [ERROR] Deploy failed.
    exit /b 1
)
echo [OK]     Deployed %PROJECT_NAME%

REM --- STEP 5: start server -------------------------------------------------
echo [STEP 5] Start integration server
if exist "%CONSOLE_LOG%" del /Q "%CONSOLE_LOG%"
start "%SERVER_NAME%" /MIN cmd /c "IntegrationServer --work-dir ""%WORK_DIR%"" --name %SERVER_NAME% --http-port-number %HTTP_PORT% --admin-rest-api %ADMIN_PORT% > ""%CONSOLE_LOG%"" 2>&1"

set /a TRIES=0
:WAIT_READY
set /a TRIES+=1
findstr /C:"BIP1991I" "%CONSOLE_LOG%" >nul 2>&1
if not errorlevel 1 goto SERVER_READY
findstr /C:"BIP2203E" /C:"BIP1988" "%CONSOLE_LOG%" >nul 2>&1
if not errorlevel 1 (
    echo [ERROR] Server refused to start. Console log:
    type "%CONSOLE_LOG%"
    exit /b 1
)
if %TRIES% GEQ 90 (
    echo [ERROR] BIP1991I not seen after 90 polls. Console log:
    type "%CONSOLE_LOG%"
    call :STOP_SERVER
    exit /b 1
)
REM Never use "timeout /t" here. With stdout redirected it aborts instantly and
REM every wait silently becomes a no-op.
ping 127.0.0.1 -n 2 >nul
goto WAIT_READY

:SERVER_READY
echo [OK]     BIP1991I after %TRIES% polls

REM --- STEP 6: verify listener ----------------------------------------------
echo [STEP 6] Verify HTTP listener
set /a TRIES=0
:WAIT_PORT
set /a TRIES+=1
netstat -an | findstr /C:":%HTTP_PORT% " | findstr /I "LISTENING" >nul 2>&1
if not errorlevel 1 goto PORT_READY
if %TRIES% GEQ 30 (
    echo [ERROR] Port %HTTP_PORT% never reached LISTENING.
    call :STOP_SERVER
    exit /b 1
)
ping 127.0.0.1 -n 2 >nul
goto WAIT_PORT

:PORT_READY
echo [OK]     Port %HTTP_PORT% LISTENING

REM --- STEP 7: drive the flow -----------------------------------------------
echo [STEP 7] Drive the six paths
del /Q "%RESULTS_DIR%\*" >nul 2>&1

REM  label            url path        request body
call :DRIVE A_build        build         "%INPUT_DIR%\clean-order.xml"
call :DRIVE B_badschema    badschema     "%INPUT_DIR%\clean-order.xml"
call :DRIVE C_parse        parse         "%INPUT_DIR%\dirty-order.xml"
call :DRIVE D_noval        noval         "%INPUT_DIR%\clean-order.xml"
call :DRIVE E_roundtrip    roundtrip     "%INPUT_DIR%\clean-order.xml"
call :DRIVE F_roundtripbad roundtripbad  "%INPUT_DIR%\clean-order.xml"
call :DRIVE G_reparse      reparse          "%INPUT_DIR%\bad-order.xml"
call :DRIVE H_reparse_od   reparseondemand  "%INPUT_DIR%\bad-order.xml"
call :DRIVE I_reparse_tch  reparsetouched   "%INPUT_DIR%\bad-order.xml"

REM Parse-happened probes. Same two URLs, fed the 0x1A document instead. An XMLNSC
REM parse MUST throw BIP5004 on that input whatever the validation settings are, so
REM a 500 here proves the deferred parse ran and a 200 proves it did not. Without
REM these, H and I returning 200 is unreadable: "parsed, validation skipped" and
REM "never parsed" produce the same status code.
call :DRIVE H2_od_dirty    reparseondemand  "%INPUT_DIR%\dirty-order.xml"
call :DRIVE I2_tch_dirty   reparsetouched   "%INPUT_DIR%\dirty-order.xml"

REM Review probes. J reads the undeclared element itself, so the deferred parse
REM cannot stop short of the violation: distinguishes "validation options dropped"
REM from "on-demand validation is lazy". K covers Parse Timing = Immediate, the
REM third value; K2's dirty doc proves whether Immediate actually parsed at the RCD.
call :DRIVE J_read_bogus   reparsereadbad   "%INPUT_DIR%\bad-order.xml"
call :DRIVE K_imm_bad      reparseimmediate "%INPUT_DIR%\bad-order.xml"
call :DRIVE K2_imm_dirty   reparseimmediate "%INPUT_DIR%\dirty-order.xml"

REM "How do we validate mid-flow" probes. Each mechanism gets the 0x1A tree and a
REM schema-violation control, because a pass without a control is unreadable.
call :DRIVE V1_valnode_1a  validatenode        "%INPUT_DIR%\clean-order.xml"
call :DRIVE V2_valnode_bad validatenodebad     "%INPUT_DIR%\clean-order.xml"
call :DRIVE W_blobround_1a rcdblobroundtrip    "%INPUT_DIR%\clean-order.xml"
call :DRIVE W2_blobrnd_bad rcdblobroundtripbad "%INPUT_DIR%\clean-order.xml"
call :DRIVE X_asbits_1a    asbitstream         "%INPUT_DIR%\clean-order.xml"
call :DRIVE X2_asbits_bad  asbitstreambad      "%INPUT_DIR%\clean-order.xml"
call :DRIVE Y_jcn_1a       jcn                 "%INPUT_DIR%\clean-order.xml"
call :DRIVE Y2_jcn_bad     jcnbad              "%INPUT_DIR%\clean-order.xml"

REM --- STEP 8: verify -------------------------------------------------------
echo [STEP 8] Collect BIP codes
copy /Y "%CONSOLE_LOG%" "%RESULTS_DIR%\server.console.log" >nul
findstr /R /C:"BIP[0-9][0-9]*[EW]" "%CONSOLE_LOG%" > "%RESULTS_DIR%\bip-errors.txt" 2>nul
echo [OK]     Console log and BIP lines saved to %RESULTS_DIR%

REM --- STEP 9: report -------------------------------------------------------
echo.
echo ==========================================================================
echo  RESULTS
echo ==========================================================================
for %%F in ("%RESULTS_DIR%\*.code") do (
    set "LBL=%%~nF"
    set /p CODE=<"%%F"
    echo   !LBL!  HTTP !CODE!
)
echo.
echo   Bodies:      %RESULTS_DIR%\*.body
echo   Console log: %RESULTS_DIR%\server.console.log
echo   Server %SERVER_NAME% left running on port %HTTP_PORT% for inspection.
echo   Stop it with: %HARNESS_DIR%stop_server.bat
echo.
exit /b 0

REM ---------------------------------------------------------------------------
:DRIVE
REM %1 = label   %2 = url path   %3 = request body file
set "LABEL=%~1"
set "URLPATH=%~2"
set "BODYFILE=%~3"
curl.exe -s -S -X POST -H "Content-Type: text/xml" --data-binary "@%BODYFILE%" -o "%RESULTS_DIR%\%LABEL%.body" -w "%%{http_code}" "http://localhost:%HTTP_PORT%/%URLPATH%" > "%RESULTS_DIR%\%LABEL%.code" 2>&1
set /p DRIVECODE=<"%RESULTS_DIR%\%LABEL%.code"
echo [OK]     %LABEL% -^> /%URLPATH%  HTTP !DRIVECODE!
REM Let the exception finish reaching the event log before the next request, so
REM per-path BIP attribution in the console log stays unambiguous.
ping 127.0.0.1 -n 2 >nul
exit /b 0

REM ---------------------------------------------------------------------------
:STOP_SERVER
REM Delegated so there is one implementation. A WINDOWTITLE taskkill filter does not
REM work here: the title belongs to the cmd wrapper while IntegrationServer.exe runs
REM as a detached child, so the kill reports success and the port stays bound, and
REM the next run then fails on a port collision it did not cause.
call "%HARNESS_DIR%stop_server.bat" >nul 2>&1
exit /b 0
