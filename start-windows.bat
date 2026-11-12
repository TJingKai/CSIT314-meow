@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install Java 17 or newer and add it to PATH.
  pause
  exit /b 1
)
if not exist meow.jar (
  echo Building the Java application first...
  call build-windows.bat
  if errorlevel 1 exit /b 1
)
echo Open http://127.0.0.1:8000 in your browser once the application starts.
echo Keep this window open. Press Ctrl+C to stop.
java -jar meow.jar --meow.seed-demo=true
if errorlevel 1 (
  echo Startup failed. Check that Java 17 or newer is installed and port 8000 is free.
  pause
  exit /b 1
)
