@echo off
REM Headless Toolkit build (mqsicreatebar) of each app in work\apps.txt, each in its own
REM workspace under work\tk\<App>. This is the Toolkit-side verdict per variant: the log
REM ends in BIP0986I (clean) or BIP0965E plus a "Problem markers list" (rejected).
REM About 45 s per app; the "exit" echo is decorative (parse-time %ERRORLEVEL%).
set BASE=%~dp0
set WORK=%BASE%work
set TK=%WORK%\tk
set OUT=%WORK%\tkbars
set MQSICREATEBAR="C:\Program Files\IBM\ACE\13.0.8.1\tools\mqsicreatebar.exe"
if not exist %OUT% mkdir %OUT%
set /p APPS=<%WORK%\apps.txt

for %%A in (%APPS%) do (
  echo === toolkit %%A ===
  %MQSICREATEBAR% -data %TK%\%%A -b %OUT%\%%A.bar -a %%A -cleanBuild > %OUT%\%%A.log 2>&1
)
echo TOOLKIT DONE
