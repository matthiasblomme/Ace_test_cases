@echo off
REM Wrapper: source the ACE environment, then run the harness.
REM This is the entry point. ibmint / IntegrationServer / mqsi* are not on PATH
REM until mqsiprofile is sourced.
call "C:\Program Files\IBM\ACE\13.0.7.2\server\bin\mqsiprofile.cmd"
if errorlevel 1 ( echo [ERROR] could not source ACE profile & exit /b 1 )
call "%~dp0deploy_and_test.bat"
