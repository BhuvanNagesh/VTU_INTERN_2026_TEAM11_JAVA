@echo off
set "MVN=%TEMP%\maven\apache-maven-3.9.6\bin\mvn.cmd"
set "POM=c:\Users\bhuva\Downloads\project-bolt-github-kqfaysnh\backend\pom.xml"
set "LOG=c:\Users\bhuva\Downloads\project-bolt-github-kqfaysnh\project\build.log"
"%MVN%" -f "%POM%" compile > "%LOG%" 2>&1
echo Exit code: %ERRORLEVEL%
