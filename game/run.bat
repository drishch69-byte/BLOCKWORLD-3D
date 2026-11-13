@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

if not exist ".logs" mkdir ".logs"
set "LOG_FILE=%CD%\.logs\latest-run.log"
set "MAVEN_VERSION=3.9.11"
set "MAVEN_HOME=%CD%\.tools\apache-maven-%MAVEN_VERSION%"
set "EXIT_CODE=1"
set "BW_CRASH_JVM_OPTS=-XX:ErrorFile=.logs/hs_err_pid%%p.log -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=.logs"
if defined MAVEN_OPTS (
    set "MAVEN_OPTS=!MAVEN_OPTS! !BW_CRASH_JVM_OPTS!"
) else (
    set "MAVEN_OPTS=!BW_CRASH_JVM_OPTS!"
)

> "%LOG_FILE%" echo ============================================================
>> "%LOG_FILE%" echo CUBEVERSE 3D 1.9.2 - CLASSIC HOTBAR CONTROLS
>> "%LOG_FILE%" echo Started: %DATE% %TIME%
>> "%LOG_FILE%" echo Project: %CD%
>> "%LOG_FILE%" echo JVM fatal reports: .logs\hs_err_pid*.log
>> "%LOG_FILE%" echo Heap dumps on OutOfMemoryError: .logs\java_pid*.hprof
>> "%LOG_FILE%" echo ============================================================

echo ============================================================
echo CUBEVERSE 3D 1.9.2 - CLASSIC HOTBAR CONTROLS
echo Log: %LOG_FILE%
echo ============================================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found. Install JDK 17 or newer.
    >> "%LOG_FILE%" echo ERROR: Java was not found in PATH.
    goto :finish
)
java -version >> "%LOG_FILE%" 2>&1

where mvn >nul 2>nul
if not errorlevel 1 (
    echo Using installed Maven...
    call mvn -B -Dstyle.color=never clean compile exec:java >> "%LOG_FILE%" 2>&1
    set "EXIT_CODE=!ERRORLEVEL!"
    goto :finish
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo Maven is not installed. Downloading Apache Maven %MAVEN_VERSION%...
    if not exist ".tools" mkdir ".tools"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path $env:TEMP 'blockworld-maven.zip'; $url='https://archive.apache.org/dist/maven/maven-3/' + $env:MAVEN_VERSION + '/binaries/apache-maven-' + $env:MAVEN_VERSION + '-bin.zip'; Invoke-WebRequest -UseBasicParsing $url -OutFile $zip; Expand-Archive -LiteralPath $zip -DestinationPath '.tools' -Force; Remove-Item $zip -Force" >> "%LOG_FILE%" 2>&1
    if errorlevel 1 (
        echo Could not download Maven. Check internet or install Apache Maven 3.9+.
        goto :finish
    )
)

echo Starting CUBEVERSE with downloaded Maven...
call "%MAVEN_HOME%\bin\mvn.cmd" -B -Dstyle.color=never clean compile exec:java >> "%LOG_FILE%" 2>&1
set "EXIT_CODE=%ERRORLEVEL%"

:finish
echo.
echo ============================================================
if not "%EXIT_CODE%"=="0" (
    echo CUBEVERSE finished with an error. Log tail:
) else (
    echo CUBEVERSE process ended. If the game window vanished unexpectedly, inspect this log:
)
echo %LOG_FILE%
echo ============================================================
powershell -NoProfile -Command "if (Test-Path -LiteralPath '%LOG_FILE%') { Get-Content -LiteralPath '%LOG_FILE%' -Tail 70 }"
for %%F in (.logs\hs_err_pid*.log) do if exist "%%F" echo JVM FATAL REPORT: %%F
for %%F in (.logs\java_pid*.hprof) do if exist "%%F" echo HEAP DUMP: %%F
echo.
echo The console will stay open so the result is not lost.
pause
exit /b %EXIT_CODE%
