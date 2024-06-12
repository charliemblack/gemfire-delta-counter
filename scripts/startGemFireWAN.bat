@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

:: Set up the APP_HOME directory
PUSHD %~dp0..
IF NOT DEFINED APP_HOME SET "APP_HOME=%CD%"
POPD

:: Default memory and JVM options
set "DEFAULT_LOCATOR_MEMORY=1g"
set "DEFAULT_SERVER_MEMORY=2g"
set "DEFAULT_JVM_OPTS=--J=-Djava.net.preferIPv4Stack=true"
set "LOCATORS_SiteA=localhost[10334]"
set "LOCATORS_SiteB=localhost[20334]"

:: Locator configuration
set "COMMON_LOCATOR_ITEMS=--initial-heap=%DEFAULT_LOCATOR_MEMORY%"
set "COMMON_LOCATOR_ITEMS=%COMMON_LOCATOR_ITEMS% --max-heap=%DEFAULT_LOCATOR_MEMORY%"
set "COMMON_LOCATOR_ITEMS=%COMMON_LOCATOR_ITEMS% %DEFAULT_JVM_OPTS%"

:: Create the locator directory
mkdir "%APP_HOME%\data\locator1"
mkdir "%APP_HOME%\data\locatorA"

echo Starting GemFire at %TIME%

:: Start locators
start "startingGemFireLocator" /min cmd /c ^
"gfsh -e ^"start locator --name=locator1 --J=-Dgemfire.distributed-system-id=1 --J=-Dgemfire.remote-locators=localhost[20334] --dir=%APP_HOME%\data\locator1 --port=10334 %COMMON_LOCATOR_ITEMS%^""

start "startingGemFireLocator" /min cmd /c ^
"gfsh -e ^"start locator --name=locatorA --J=-Dgemfire.distributed-system-id=2 --J=-Dgemfire.remote-locators=localhost[10334] --dir=%APP_HOME%\data\locatorA --port=20334 %COMMON_LOCATOR_ITEMS% --http-service-port=6080 --J=-Dgemfire.jmx-manager-port=2099^""

:: Specify the delay between each check
set "delay=5"

:: Loop through each port
for %%p in (7070 6080) do (
    call :CHECK_PORT %%p
)

echo Locators started at %TIME%

:: Server configuration
set "COMMON_SERVER_ITEMS=--J=-Xmx%DEFAULT_SERVER_MEMORY% --J=-Xms%DEFAULT_SERVER_MEMORY%"
set "COMMON_SERVER_ITEMS=%COMMON_SERVER_ITEMS% %DEFAULT_JVM_OPTS%"
set "COMMON_SERVER_ITEMS=%COMMON_SERVER_ITEMS% --server-port=0"
set "COMMON_SERVER_ITEMS=%COMMON_SERVER_ITEMS% --rebalance"

:: Remove process file if it exists
del /F /Q "%APP_HOME%\data\processfile.txt" 2>nul

:: Start multiple servers for SiteA
for %%i in (1 2) do (
    set /a port=7070 + %%i * 10
    start "startingGemFireServer%%i" /min cmd /c ^
    "gfsh -e ^"connect --locator=%LOCATORS_SiteA%^" -e ^"start server --name=server%%i --J=-Dgemfire.remote-locators=localhost[20334] --J=-Dgemfire.distributed-system-id=1 --locators=localhost[10334] --dir=%APP_HOME%\data\server%%i --start-rest-api=true --http-service-port=!port! %COMMON_SERVER_ITEMS%^" ^& echo server%%i ^>^> %APP_HOME%\data\processfile.txt"
)

:: Start multiple servers for SiteB
for %%i in (3 4) do (
    set /a port=7070 + %%i * 10
    start "startingGemFireServer%%i" /min cmd /c ^
    "gfsh -e ^"connect --locator=%LOCATORS_SiteB%^" -e ^"start server --name=server%%i --J=-Dgemfire.distributed-system-id=2 --J=-Dgemfire.remote-locators=localhost[10334] --locators=localhost[20334] --dir=%APP_HOME%\data\server%%i --start-rest-api=true --http-service-port=!port! %COMMON_SERVER_ITEMS%^" ^& echo server%%i ^>^> %APP_HOME%\data\processfile.txt"
)

:: Monitor server startup
:LOOP
set "allStarted=true"
for %%i in (1 2 3 4) do (
    findstr /I /C:"server%%i" "%APP_HOME%\data\processfile.txt" >nul 2>&1
    if ERRORLEVEL 1 (
        set "allStarted=false"
    )
)

:: Check if all servers have completed
if "%allStarted%" equ "false" (
    c:\Windows\System32\timeout.exe /T %delay% /NOBREAK >nul 2>&1
    goto LOOP
)
echo Servers started at %TIME%

:CONTINUE

start /wait cmd /c "gfsh -e ^"connect --locator=localhost[10334]^" -e ^"create gateway-receiver^" -e ^"create gateway-sender --id=sender --remote-distributed-system-id=2 --parallel=true^" -e ^"create region --name=accumulatorRegion --type=PARTITION_REDUNDANT --gateway-sender-id=sender --enable-concurrency-checks=false^" -e ^"deploy --jar=%APP_HOME%/build/libs/gemfire-delta-counter-plain.jar^" -e ^"list regions^""
echo Finished Config 10334

start /wait cmd /c "gfsh -e ^"connect --locator=localhost[20334]^" -e ^"create gateway-receiver^" -e ^"create gateway-sender --id=sender --remote-distributed-system-id=1 --parallel=true^" -e ^"create region --name=accumulatorRegion --type=PARTITION_REDUNDANT --gateway-sender-id=sender --enable-concurrency-checks=false^" -e ^"deploy --jar=%APP_HOME%/build/libs/gemfire-delta-counter-plain.jar^" -e ^"list regions^""
echo Finished Config 20334

echo GemFire and configured at %TIME%
goto :EOF

:CHECK_PORT
set "port=%1"

:PORT_CHECK_LOOP
:: Use PowerShell to check if the port is open
powershell -Command "if ((Test-NetConnection -ComputerName 'localhost' -Port %port% -WarningAction SilentlyContinue).TcpTestSucceeded) { exit 0 } else { exit 1 }"
if %ERRORLEVEL% equ 0 (
    goto END_PORT_CHECK
)

:: Wait for the specified delay
c:\Windows\System32\timeout.exe /T %delay% /NOBREAK >nul

:: Retry the port check
goto PORT_CHECK_LOOP

:END_PORT_CHECK
goto :EOF
