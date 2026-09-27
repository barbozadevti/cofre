@echo off
chcp 65001 >nul
title Cofre - terminal da agencia

cd /d "%~dp0"
call :java || goto :fim
call :compilar || goto :fim
"%JAVA%" -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar target\cofre.jar --terminal
:fim
pause
exit /b

:java
rem Procura um Java 21+: JAVA_HOME, depois a pasta do Eclipse Temurin, depois o PATH.
set "JAVA="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-2*") do set "JAVA=%%d\bin\java.exe"
if not defined JAVA set "JAVA=java"
rem O "." da expressao casa com as aspas de: version "21.0.x"
"%JAVA%" -version 2>&1 | findstr /r /c:"version .2[1-9]" /c:"version .[3-9][0-9]" >nul
if errorlevel 1 (
  echo   O Cofre precisa do Java 21 ou mais recente. Instale em https://adoptium.net
  exit /b 1
)
for %%j in ("%JAVA%") do set "JAVA_HOME=%%~dpj.."
exit /b 0

:compilar
if exist "target\cofre.jar" exit /b 0
echo   Primeira execucao: compilando o Cofre (leva cerca de 1 minuto)...
call mvn -q -DskipTests package
exit /b %errorlevel%
