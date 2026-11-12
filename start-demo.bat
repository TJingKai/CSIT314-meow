@echo off
rem Fresh demo: wipes the database, creates the demo accounts, loads the generated test data.
setlocal
cd /d "%~dp0"
call test\prepare-demo.bat 20261108
if errorlevel 1 (
  echo.
  echo Demo setup failed.
  pause
  exit /b 1
)
cd /d "%~dp0"
if "%MEOW_PORT%"=="" set MEOW_PORT=8000
echo.
echo ==================================================================
echo  MEOW is ready in demo mode:  http://127.0.0.1:%MEOW_PORT%
echo.
echo  Admin      admin@meow.demo      MEOWDemo!2026
echo  Manager    manager@meow.demo    MEOWDemo!2026
echo  Designer   designer@meow.demo   MEOWDemo!2026
echo  Customer   customer@meow.demo   MEOWDemo!2026
echo  Generated users and passwords:  test\testdata\users.csv
echo.
echo  The server runs in the "MEOW server" window. Close it to stop.
echo ==================================================================
start "" http://127.0.0.1:%MEOW_PORT%
pause
