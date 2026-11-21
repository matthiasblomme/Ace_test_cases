@echo off
REM Starts the ACE MCP demo integration server.
REM Usage: start-demo-server.cmd <vaultKey>
REM Copy this script to the demo root, outside any git repo: the folder that holds the work
REM dir MCPDEMO, the vault and demo-credentials.txt. The vault key is in that file.
REM
REM Ports: 7620 admin REST, 7820 HTTP, 7860 HTTPS, 7650 MCP.Admin, 7750 MCP.Runtime.
REM The server never exits on its own. Ctrl-C in its window stops it.

if "%~1"=="" goto :nokey
if not defined ACE_INSTALL set "ACE_INSTALL=C:\Program Files\IBM\ACE\13.0.9.0"

call "%ACE_INSTALL%\server\bin\mqsiprofile.cmd" >nul
IntegrationServer --work-dir "%~dp0MCPDEMO" --name ACEMCPDEMO --ext-vault-key %~1 --console-log
goto :eof

:nokey
echo Vault key required. Read it from %~dp0demo-credentials.txt
exit /b 1
