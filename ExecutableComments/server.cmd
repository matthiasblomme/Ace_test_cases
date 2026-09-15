@echo off
REM Start the standalone integration server on admin 7603 / HTTP 7801 with console logging.
REM chcp 1252 is mandatory: codepage 65001 makes every flow fail at startup with BIP2132E.
REM Wait for BIP1991I before sending requests. Stop it port-scoped (see TESTING.md), never
REM with taskkill /IM IntegrationServer.exe - that kills every integration server on the box.
call "C:\Program Files\IBM\ACE\13.0.8.1\server\bin\mqsiprofile.cmd"
set BASE=%~dp0
chcp 1252
IntegrationServer --work-dir %BASE%work\sis --name ExecCommentSrv --admin-rest-api 7603 --http-port-number 7801 --console-log
