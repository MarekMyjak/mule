@echo off
setlocal
rem %~dp0 is location of current script under NT
set REALPATH=%~dp0
set BASE_DIR=%REALPATH:~0,-5%

java -jar "%BASE_DIR%\tools\agent-setup-1.1.1-amc-final.jar" %*