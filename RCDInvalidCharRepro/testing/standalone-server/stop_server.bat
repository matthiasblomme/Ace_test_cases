@echo off
setlocal

REM Stops the integration server this harness started.
REM
REM Matches on the work directory in the process command line, not on the window
REM title. "start" gives the title to the cmd wrapper while IntegrationServer.exe
REM runs as a detached child, so a WINDOWTITLE filter reports success and leaves the
REM server running on its port.

if not defined SERVER_NAME set "SERVER_NAME=RCD_TEST"
if not defined BASE_DIR    set "BASE_DIR=C:\temp\rcdrepro"
set "WORK_DIR=%BASE_DIR%\%SERVER_NAME%"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$wd = '%WORK_DIR%';" ^
  "$hits = @(Get-CimInstance Win32_Process -Filter \"Name='IntegrationServer.exe'\" | Where-Object { $_.CommandLine -like ('*' + $wd + '*') });" ^
  "if ($hits.Count -eq 0) { Write-Output ('No IntegrationServer running for ' + $wd); exit 0 };" ^
  "foreach ($h in $hits) { Stop-Process -Id $h.ProcessId -Force; Write-Output ('Stopped PID ' + $h.ProcessId) }"

exit /b 0
