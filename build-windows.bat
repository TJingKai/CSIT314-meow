@echo off
setlocal
cd /d "%~dp0"
call mvnw.cmd clean verify
if errorlevel 1 (
  echo Build or tests failed. See the error above.
  pause
  exit /b 1
)
copy /Y target\meow.jar meow.jar >nul
if errorlevel 1 exit /b 1
echo Build complete. Run start-windows.bat to launch the new version.
