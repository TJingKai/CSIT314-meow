@echo off
rem Test run on a fresh database with the generated test data (fixed seed 20261108).
setlocal
cd /d "%~dp0"
set SEED=20261108
if not "%~1"=="" set SEED=%~1
call test\prepare-demo.bat %SEED%
if errorlevel 1 (
  echo.
  echo Setup failed, tests were not run.
  pause
  exit /b 1
)
cd /d "%~dp0"
if "%MEOW_PORT%"=="" set MEOW_PORT=8000
echo.
echo Running the test cases (data seed %SEED%) ...
cd test
java TestRunner.java http://127.0.0.1:%MEOW_PORT%
set RESULT=%ERRORLEVEL%
echo.
if "%RESULT%"=="0" (
  echo All test cases passed. Results: test\testdata\test_results.csv
) else (
  echo Some test cases failed. Results: test\testdata\test_results.csv
)
echo The server is still running in the "MEOW server" window.
pause
exit /b %RESULT%
