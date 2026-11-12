@echo off
rem Shared by start-demo.bat and run-tests.bat. Do not run on its own.
rem   1. stop any MEOW server on the port      4. generate test data (seed = %1)
rem   2. delete the old database and uploads   5. check the CSV files
rem   3. start MEOW with the demo accounts     6. load them, then check the database
setlocal
cd /d "%~dp0.."
set SEED=%~1
if "%SEED%"=="" set SEED=20261108
if "%MEOW_PORT%"=="" set MEOW_PORT=8000
set MEOW_URL=http://127.0.0.1:%MEOW_PORT%

where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install a Java 17 or newer JDK and add it to PATH.
  exit /b 1
)
where javac >nul 2>nul
if errorlevel 1 (
  echo javac was not found. The test tools need a JDK ^(not only a JRE^), Java 17 or newer.
  exit /b 1
)
if not exist meow.jar (
  echo meow.jar is missing. Run build-windows.bat first.
  exit /b 1
)

echo [1/6] Stopping any MEOW server on port %MEOW_PORT% ...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /r /c:":%MEOW_PORT% .*LISTENING"') do (
  tasklist /FI "PID eq %%p" | find /i "java" >nul && taskkill /F /PID %%p >nul 2>&1
)
timeout /t 2 /nobreak >nul
netstat -ano | findstr /r /c:":%MEOW_PORT% .*LISTENING" >nul
if not errorlevel 1 (
  echo Port %MEOW_PORT% is used by another program. Close it or set MEOW_PORT to a free port.
  exit /b 1
)

echo [2/6] Clearing the old database ...
if exist instance rmdir /s /q instance
if exist instance (
  echo Could not delete the instance folder. Close any program using it and try again.
  exit /b 1
)

echo [3/6] Starting MEOW with the demo accounts ...
start "MEOW server" java -jar meow.jar --meow.seed-demo=true --server.port=%MEOW_PORT%

cd test
echo [4/6] Generating test data (seed %SEED%) ...
java TestDataGenerator.java %SEED%
if errorlevel 1 (
  echo Test data generation failed.
  exit /b 1
)

echo [5/6] Checking the generated CSV files ...
java DataCheck.java csv
if errorlevel 1 (
  echo The generated data has discrepancies. See the list above.
  exit /b 1
)

echo [6/6] Loading the test data and checking the database ...
java TestDataLoader.java %MEOW_URL%
if errorlevel 1 (
  echo Loading failed. See the messages above and the "MEOW server" window.
  exit /b 1
)
java DataCheck.java db
if errorlevel 1 (
  echo The database does not match the generated data. See the list above.
  exit /b 1
)
exit /b 0
