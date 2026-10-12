@echo off
setlocal
where gradle >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
 echo Gradle no se encuentra instalado. Abre el proyecto con Android Studio o instala Gradle 8.9.
 exit /b 1
)
call gradle --no-daemon assembleDebug
if %ERRORLEVEL% NEQ 0 exit /b 1
echo APK creado en app\build\outputs\apk\debug\app-debug.apk