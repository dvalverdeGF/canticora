@echo off
REM =============================================================================
REM  Empaqueta Canticora (escritorio) como MSIX y lo firma para probarlo en Windows.
REM
REM  Requisitos:
REM    - Imagen de la app:  gradlew :desktopApp:createDistributable  (runtime JVM embebido)
REM    - Iconos del paquete:  packaging/msix/Assets/*.png
REM        (generados desde WSL con: python3 packaging/msix/generate_assets.py)
REM    - makeappx.exe / signtool.exe en .sdkbt\bin\10.0.26100.0\x64
REM        (extraidos del paquete NuGet Microsoft.Windows.SDK.BuildTools, sin admin)
REM
REM  Uso:  packaging\msix\build_msix.bat            -> MSIX de desarrollo (firmado, pruebas locales)
REM        packaging\msix\build_msix.bat store      -> MSIX para la Store (sin firmar, identidad Partner Center)
REM =============================================================================
setlocal

set ROOT=%~dp0..\..
set APPIMAGE=%ROOT%\desktopApp\build\compose\binaries\main\app
set MSIXDIR=%ROOT%\packaging\msix
set STAGE=%ROOT%\desktopApp\build\msix-stage
set OUT=%ROOT%\desktopApp\build\compose\binaries\main\msix
set SDK=%ROOT%\.sdkbt\bin\10.0.26100.0\x64

REM --- Versión: FUENTE ÚNICA en gradle.properties (ADR-022) -----------------
REM  El numero NO se escribe aqui ni en los manifiestos: se lee de `canticora.version`.
REM  El 4.o digito del MSIX lo exige la Store y va a 0 -> 1.1.3  =>  1.1.3.0
set VERSION=
for /f "usebackq tokens=1,* delims==" %%A in ("%ROOT%\gradle.properties") do (
    if /i "%%A"=="canticora.version" set VERSION=%%B
)
if "%VERSION%"=="" (
    echo ERROR: falta `canticora.version` en gradle.properties
    exit /b 1
)
set MSIXVERSION=%VERSION%.0

REM --- Modo: "dev" (por defecto) o "store" -----------------------------------
set MODE=%1
if "%MODE%"=="" set MODE=dev
if /i "%MODE%"=="store" (
    set MANIFEST=%MSIXDIR%\AppxManifest.store.xml
    set PACKAGENAME=Canticora-%VERSION%-store.msix
) else (
    set MANIFEST=%MSIXDIR%\AppxManifest.xml
    set PACKAGENAME=Canticora-%VERSION%.msix
)

if not exist "%SDK%\makeappx.exe" (
    echo ERROR: falta makeappx.exe en %SDK%
    exit /b 1
)
if not exist "%MANIFEST%" (
    echo ERROR: falta el manifiesto %MANIFEST%
    exit /b 1
)
if not exist "%APPIMAGE%\Canticora\Canticora.exe" (
    echo ERROR: falta la imagen de la app. Ejecuta: gradlew :desktopApp:createDistributable
    exit /b 1
)
if not exist "%MSIXDIR%\Assets\StoreLogo.png" (
    echo ERROR: faltan los iconos. Ejecuta desde WSL: python3 packaging/msix/generate_assets.py
    exit /b 1
)

echo [1/5] Preparando carpeta de empaquetado...  (modo: %MODE%)
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%"
xcopy /e /i /q /y "%APPIMAGE%\*" "%STAGE%\" >nul || exit /b 1
xcopy /e /i /q /y "%MSIXDIR%\Assets" "%STAGE%\Assets\" >nul || exit /b 1
copy /y "%MANIFEST%" "%STAGE%\AppxManifest.xml" >nul || exit /b 1

REM  La version del manifiesto se fija aqui desde gradle.properties, para que no haya
REM  dos numeros que se puedan desincronizar (ADR-018). El manifiesto lleva un
REM  marcador: Version="0.0.0.0" -> Version="%MSIXVERSION%".
powershell -NoProfile -Command ^
  "$f='%STAGE%\AppxManifest.xml'; $t=Get-Content -Raw -LiteralPath $f; $t=$t -replace 'Version=\"0\.0\.0\.0\"','Version=\"%MSIXVERSION%\"'; Set-Content -NoNewline -LiteralPath $f -Value $t" || exit /b 1
findstr /c:"Version=\"%MSIXVERSION%\"" "%STAGE%\AppxManifest.xml" >nul || (
    echo ERROR: no se pudo fijar la version %MSIXVERSION% en el manifiesto.
    echo        Revisa que AppxManifest tenga Version="0.0.0.0".
    exit /b 1
)
echo     Version del paquete: %MSIXVERSION%

REM --- resources.pri ---------------------------------------------------------
REM  OBLIGATORIO para que Windows resuelva los cualificadores de recursos
REM  (targetsize y altform-unplated) de los iconos. Sin este fichero Windows NO
REM  encuentra Square44x44Logo.targetsize-N_altform-unplated.png y pinta el icono
REM  "plated" (con la placa de fondo del color de acento).
echo [1b/5] Generando resources.pri (resolucion de cualificadores de iconos)...
if not exist "%MSIXDIR%\priconfig.xml" (
    "%SDK%\makepri.exe" createconfig /cf "%MSIXDIR%\priconfig.xml" /dq en-US /o >nul || exit /b 1
)
"%SDK%\makepri.exe" new /pr "%STAGE%" /cf "%MSIXDIR%\priconfig.xml" /of "%STAGE%\resources.pri" /o >nul || exit /b 1

echo [2/5] Empaquetando (makeappx)...
if not exist "%OUT%" mkdir "%OUT%"
set MSIX=%OUT%\%PACKAGENAME%
if exist "%MSIX%" del /q "%MSIX%"
"%SDK%\makeappx.exe" pack /d "%STAGE%" /p "%MSIX%" /o || exit /b 1

REM --- Modo Store: el paquete va SIN firmar; la Store lo re-firma al certificarlo. ---
if /i "%MODE%"=="store" goto :store_done

echo [3/5] Creando certificado autofirmado (si no existe)...
set P12=%MSIXDIR%\LaudateDev.pfx
if not exist "%P12%" (
    powershell -NoProfile -Command ^
      "$c = New-SelfSignedCertificate -Type Custom -Subject 'CN=LaudateDev' -KeyUsage DigitalSignature -FriendlyName 'Canticora Dev' -CertStoreLocation 'Cert:\CurrentUser\My' -TextExtension @('2.5.29.37={text}1.3.6.1.5.5.7.3.3','2.5.29.19={text}'); $p = ConvertTo-SecureString -String 'laudate' -Force -AsPlainText; Export-PfxCertificate -Cert $c -FilePath '%P12%' -Password $p | Out-Null; Write-Host '    Certificado creado.'"
)

echo [4/5] Firmando el paquete (certificado de desarrollo)...
"%SDK%\signtool.exe" sign /fd SHA256 /a /f "%P12%" /p "laudate" "%MSIX%" || (
    echo ERROR al firmar. Comprueba el certificado.
    exit /b 1
)

echo [5/5] Listo. MSIX de DESARROLLO generado y firmado:
echo     %MSIX%
echo.
echo Para instalarlo:
echo   1) Instala el certificado en "Entidades de certificacion raiz de confianza" (Usuario actual):
echo      PowerShell:  Import-PfxCertificate -FilePath "packaging\msix\LaudateDev.pfx" -CertStoreLocation Cert:\CurrentUser\Root -Password (ConvertTo-SecureString laudate -AsPlainText -Force)
echo   2) Doble clic en el .msix  ^-^>  Instalar
echo.
endlocal
goto :eof

:store_done
echo [3-5/5] Modo STORE: el paquete queda SIN firmar (lo re-firma la Store).
echo.
echo [5/5] Listo. MSIX para la MICROSOFT STORE:
echo     %MSIX%
echo.
echo Siguiente:
echo   1) Comprueba la identidad:  powershell -ExecutionPolicy Bypass -File packaging\msix\verify_store_package.ps1
echo   2) Pasa el WACK:            powershell -ExecutionPolicy Bypass -File packaging\msix\wack.ps1
echo   3) Subelo en Partner Center ^> Canticora ^> Envio ^> Paquetes.
echo.
endlocal

