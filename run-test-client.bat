@echo off
setlocal
cd /d "%~dp0"
if exist "%USERPROFILE%\.jdks\temurin-11.0.30\bin\java.exe" set "JAVA_HOME=%USERPROFILE%\.jdks\temurin-11.0.30"
call gradlew.bat run
if errorlevel 1 pause
