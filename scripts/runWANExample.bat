@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

:: Set up the APP_HOME directory
PUSHD %~dp0..
IF NOT DEFINED APP_HOME SET "APP_HOME=%CD%"
POPD

start "Jamming data In site 1" cmd /c "java -jar %APP_HOME%/build/libs/gemfire-delta-counter.jar --spring.profiles.active=site1 & pause"
start "Jamming data In site 2" cmd /c "java -jar %APP_HOME%/build/libs/gemfire-delta-counter.jar --spring.profiles.active=site2 & pause"
