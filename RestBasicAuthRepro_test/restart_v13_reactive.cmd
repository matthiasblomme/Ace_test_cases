@echo off
call "C:\Program Files\IBM\ACE\13.0.7.2\server\bin\mqsiprofile.cmd" >nul
if errorlevel 1 (echo profile failed & exit /b 1)
echo --SET-PREEMPT-EMPTY--
mqsichangeproperties TEST_V13 -e IS1 -o ComIbmSocketConnectionManager -n preemptiveAuthType -v ""
echo --STOP--
mqsistop TEST_V13
echo --START--
mqsistart TEST_V13
echo --DONE--
