@echo off
cd /d "%~dp0"
if exist "C:\Program Files\Java\jdk-26.0.1" set "JAVA_HOME=C:\Program Files\Java\jdk-26.0.1"
if exist "C:\Program Files\Java\jdk-26" if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-26"
if exist "C:\Program Files\Java\jdk-25" if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-25"
if not defined JAVA_HOME (
    echo ERROR: Java 25+ required for Minecraft 26.x. Install JDK 26 or 25.
    pause
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME=%JAVA_HOME%
echo.
echo Building mod jar (clean + jar so the main jar is always rewritten)...
rem "build" alone can leave the main jar UP-TO-DATE while only *-sources.jar changes.
rem Use clean jar so build\libs\*-fabric-*.jar (without -sources) is freshly produced.
call gradlew.bat clean jar --offline
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Offline build failed, retrying online...
    call gradlew.bat clean jar
)
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Build failed with error code %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo Done. Use this jar in your mods folder (NOT the *-sources.jar):
echo.
for %%F in ("build\libs\*-fabric-*.jar") do (
    echo %%~nxF | findstr /i /v "sources" >nul && (
        echo   %%~fF
        echo   %%~nxF  ^|  %%~zF bytes  ^|  %%~tF
    )
)
echo.
echo All jars in build\libs:
dir /b "build\libs\*.jar"
echo.
pause
exit /b 0
