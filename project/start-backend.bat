@echo off
set "MVN=%TEMP%\maven\apache-maven-3.9.6\bin\mvn.cmd"
set "POM=c:\Users\bhuva\Downloads\project-bolt-github-kqfaysnh\backend\pom.xml"
"%MVN%" -f "%POM%" spring-boot:run
