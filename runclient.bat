@echo off
cd /d "%~dp0"
if exist "C:\Program Files\Java\jdk-21.0.11" set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
if exist "C:\Program Files\Java\jdk-21" if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-21"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME=%JAVA_HOME%
echo Starting Minecraft Client...
call gradlew.bat runClient
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Build failed with error code %ERRORLEVEL%
    pause
)
exit /b %ERRORLEVEL%
