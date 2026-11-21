@echo off
REM Off camera, once per Command Prompt terminal, before recording:
REM     call <demo_root>\demo-env.cmd
REM Copy this script to the demo root, next to demo-credentials.txt (outside any git repo).
REM Loads VK (vault key) and PW (database password) from demo-credentials.txt without
REM printing either, and the ACE environment so ibmint works in this terminal.
REM demo-credentials.txt has one line "Vault key: <vaultKey>" and one line
REM "Password: <password>".
REM On camera you then type %VK% and %PW%, never the values.
REM No setlocal on purpose: the variables must survive into the calling terminal.
set "VK="
set "PW="
for /f "tokens=1,* delims=:" %%a in ('findstr /b /c:"Vault key" "%~dp0demo-credentials.txt"') do set "VK=%%b"
for /f "tokens=1,* delims=:" %%a in ('findstr /b /c:"Password" "%~dp0demo-credentials.txt"') do set "PW=%%b"
if not defined VK echo VAULT KEY NOT FOUND & exit /b 2
if not defined PW echo PASSWORD NOT FOUND & exit /b 2
set "VK=%VK: =%"
set "PW=%PW: =%"
if not defined ACE_INSTALL set "ACE_INSTALL=C:\Program Files\IBM\ACE\13.0.9.0"
call "%ACE_INSTALL%\server\bin\mqsiprofile.cmd" >nul
echo Demo environment loaded: VK, PW and the ACE profile are set in this terminal.
