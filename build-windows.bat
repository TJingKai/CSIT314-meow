@echo off
setlocal
cd /d "%~dp0"
rem Add "skipformat" to build without the code-format check, e.g. build-windows.bat skipformat
set EXTRA=
if /i "%~1"=="skipformat" set EXTRA=-Dspotless.check.skip=true
call mvnw.cmd clean verify %EXTRA%
if errorlevel 1 (
  echo Build or tests failed. See the error above.
  echo If only the format check failed, try: build-windows.bat skipformat
  pause
  exit /b 1
)
copy /Y target\meow.jar meow.jar >nul
if errorlevel 1 exit /b 1
echo Build complete. Run start-demo.bat to launch the new version.
pause
