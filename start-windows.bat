@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if errorlevel 1 (
  echo Python was not found. Install Python 3.10 or newer from python.org with the launcher enabled.
  pause
  exit /b 1
)
if not exist .venv\Scripts\python.exe (
  py -3 -m venv .venv
  if errorlevel 1 goto :error
)
.venv\Scripts\python.exe -m pip install -r requirements.txt
if errorlevel 1 goto :error
echo Open http://127.0.0.1:8000 in your browser. Press Ctrl+C here to stop.
.venv\Scripts\python.exe run.py --demo
if errorlevel 1 goto :error
exit /b 0
:error
echo Setup or server startup failed. See the error above.
pause
exit /b 1
