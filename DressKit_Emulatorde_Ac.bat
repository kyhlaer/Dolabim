@echo off
chcp 65001 >nul
pushd "%~dp0"
set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "EMU=%SDK%\emulator\emulator.exe"
set "ADB=%SDK%\platform-tools\adb.exe"
set "AVD_DIR=%USERPROFILE%\.android\avd\DressKitPhone.avd"

tasklist /FI "IMAGENAME eq qemu-system-x86_64.exe" 2>nul | find /I "qemu-system-x86_64.exe" >nul
if errorlevel 1 (
    del /F /Q "%AVD_DIR%\*.lock" >nul 2>&1
    for /D %%d in ("%AVD_DIR%\*.lock") do rmdir /S /Q "%%d" >nul 2>&1
    echo Dolabim Sanal Telefon Baslatiliyor...
    start "" "%EMU%" -avd DressKitPhone -no-boot-anim -no-snapshot-load
) else (
    echo Acik olan emulatore baglaniliyor...
)

echo Telefonun acilmasi bekleniyor...
"%ADB%" start-server >nul 2>&1
"%ADB%" wait-for-device

:waitboot
set "BOOT="
for /f "usebackq delims=" %%i in (`"%ADB%" shell getprop sys.boot_completed 2^>nul`) do set "BOOT=%%i"
if not "%BOOT:~0,1%"=="1" (
    timeout /t 2 /nobreak >nul
    goto waitboot
)

timeout /t 2 /nobreak >nul
echo Dolabim v1.41 yukleniyor ve aciliyor...
"%ADB%" install -r "Dolabim.apk"
if errorlevel 1 (
    echo Yukleme sirasinda hata olustu.
    popd
    pause
    exit /b 1
)
"%ADB%" shell am start -n com.dresskit.wardrobe/.MainActivity

echo.
echo Dolabim acildi! Sanal telefon ekranda acik kalacaktir.
popd
timeout /t 3 >nul
