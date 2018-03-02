@REM
@REM (c) 2003-2010 MuleSoft, Inc. This software is protected under international copyright
@REM law. All use of this software is subject to MuleSoft's Master Subscription Agreement
@REM (or other master license agreement) separately entered into in writing between you and
@REM MuleSoft. If such an agreement is not in place, you may not use the software.
@REM

@echo off

:: MULE_HOME must be set
if "%MULE_HOME%" == "" (
   echo You must set the MULE_HOME environment variable before starting this script
   goto :eof
)

:: strip any trailing path separators
if not _%MULE_HOME:~-1%==_\ goto launch
set MULE_HOME=%MULE_HOME:~0,-1%

:launch
call "%MULE_HOME%\bin\launcher" "%MULE_HOME%\bin\populate_m2_repo.groovy" %*
