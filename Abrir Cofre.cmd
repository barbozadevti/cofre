@echo off
chcp 65001 >nul
title Cofre

rem Se o Cofre ja estiver rodando, so abre o navegador.
powershell -NoProfile -Command "try { Invoke-WebRequest http://localhost:5230/health -UseBasicParsing -TimeoutSec 2 | Out-Null; exit 0 } catch { exit 1 }"
if %errorlevel%==0 (
  start "" http://localhost:5230
  exit /b
)

cd /d "%~dp0"
call :java || goto :fim
call :compilar || goto :fim

echo.
echo   Cofre - iniciando o servidor...
echo   O navegador abre sozinho quando estiver pronto.
echo   Para encerrar, feche esta janela.
echo.
"%JAVA%" -jar target\cofre.jar --cofre.abrir-navegador=true
:fim
if errorlevel 1 pause
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
