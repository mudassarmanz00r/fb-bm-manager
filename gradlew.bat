@rem Lightweight Gradle bootstrap for the FB BM Page Manager project (Windows).
@rem Downloads the Gradle 8.7 distribution once into %GRADLE_USER_HOME%
@rem (default %USERPROFILE%\.gradle) and reuses it afterwards.
@echo off
setlocal
set GRADLE_VERSION=8.7
set DIST_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set DIST_ROOT=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin
set DIST_DIR=%DIST_ROOT%\gradle-%GRADLE_VERSION%
set ZIP_FILE=%DIST_ROOT%\gradle-%GRADLE_VERSION%-bin.zip

if not exist "%DIST_DIR%\bin\gradle.bat" (
    echo Downloading Gradle %GRADLE_VERSION% ^(one-time^)...
    if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%ZIP_FILE%'"
    powershell -NoProfile -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%DIST_ROOT%' -Force"
)
"%DIST_DIR%\bin\gradle.bat" %*
