@echo off
chcp 65001 >nul
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "SRC=%~dp0TestFotolar"

if not exist "%SRC%" (
    mkdir "%SRC%"
    echo "TestFotolar" klasoru olusturuldu!
    echo Lutfen kiyafet fotograflarinizi bu klasorun icine kopyalayip tekrar calistirin.
    explorer "%SRC%"
    pause
    exit /b 0
)

echo Emülatöre fotograflar gonderiliyor...
"%ADB%" shell mkdir -p /sdcard/Pictures/DressKit

set COUNT=0
for %%f in ("%SRC%\*.jpg" "%SRC%\*.jpeg" "%SRC%\*.png" "%SRC%\*.webp") do (
    if exist "%%f" (
        set /a COUNT+=1
        echo Gonderiliyor: %%~nxf
        "%ADB%" push "%%f" "/sdcard/Pictures/DressKit/%%~nxf" >nul
        "%ADB%" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Pictures/DressKit/%%~nxf" >nul
    )
)

if %COUNT%==0 (
    echo "TestFotolar" klasorunde hic fotograf (.jpg, .png) bulunamadi!
    explorer "%SRC%"
    pause
    exit /b 0
)

echo.
echo Toplam %COUNT% fotograf emülatör galerisine eklendi!
echo DressKit icinde "Galeriden Sec" butonuna basip gorebilirsiniz.
pause
