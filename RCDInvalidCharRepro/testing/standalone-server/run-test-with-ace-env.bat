@echo off
REM Sources the ACE environment, then runs the harness. ibmint, mqsicreateworkdir
REM and IntegrationServer are not on PATH until mqsiprofile has been called.
if not defined ACE_VERSION set "ACE_VERSION=13.0.8.1"
set "ACE_HOME=C:\Program Files\IBM\ACE\%ACE_VERSION%"
if not exist "%ACE_HOME%\server\bin\mqsiprofile.cmd" (
    echo [ERROR] No ACE install at %ACE_HOME%. Set ACE_VERSION and retry.
    exit /b 1
)
call "%ACE_HOME%\server\bin\mqsiprofile.cmd" >nul
call "%~dp0deploy_and_test.bat" %*
exit /b %ERRORLEVEL%
