@echo off
REM Package every app listed in work\apps.txt with ibmint and deploy the BARs into one
REM standalone work dir (work\sis). Run with the integration server STOPPED.
REM Note: %ERRORLEVEL% inside a for-body is expanded at parse time, so the "exit" echoes
REM below are decorative - read the BIP messages (BIP1853I / BIP8071I) for the verdict.
call "C:\Program Files\IBM\ACE\13.0.8.1\server\bin\mqsiprofile.cmd"
set BASE=%~dp0
set WORK=%BASE%work
set WS=%WORK%\ws
set BARS=%WORK%\bars
set WD=%WORK%\sis
if not exist %BARS% mkdir %BARS%
set /p APPS=<%WORK%\apps.txt

for %%A in (%APPS%) do (
  echo === package %%A ===
  ibmint package --input-path %WS%\%%A --output-bar-file %BARS%\%%A.bar
)

if not exist %WD%\server.conf.yaml (
  echo === mqsicreateworkdir ===
  call mqsicreateworkdir %WD%
)

for %%A in (%APPS%) do (
  echo === deploy %%A ===
  ibmint deploy --input-bar-file %BARS%\%%A.bar --output-work-directory %WD%
)
echo BUILD DONE
