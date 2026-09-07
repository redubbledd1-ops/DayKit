@echo off
setlocal
cd /d "%~dp0"
echo Stopping Gradle daemons - releases file locks on Windows...
call gradlew.bat --stop >nul 2>&1
echo Removing app\build output (KSP, javac, etc.) - fixes AccessDenied on Windows...
if exist "app\build" rmdir /s /q "app\build"
echo Running assembleDebug...
call gradlew.bat assembleDebug %*
exit /b %ERRORLEVEL%
