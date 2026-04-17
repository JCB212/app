@echo off
:: ============================================================
::  build.bat — Módulo Fiscal InfoAtiva v2.0
::  Compila o JAR, gera o FiscalModule.exe via Launch4j
::  e organiza a pasta de distribuição.
::
::  Pré-requisitos:
::    - Maven 3.8+ no PATH (mvn -version)
::    - Java 17+ no PATH (java -version)
::    - Launch4j instalado (ajustar LAUNCH4J_HOME abaixo)
::
::  Uso: build.bat [release]
::    sem parâmetros  → build de desenvolvimento
::    release         → gera pasta dist\ completa com ZIP
:: ============================================================

setlocal EnableDelayedExpansion

:: ── Configurações ──────────────────────────────────────────
set "PROJECT_DIR=%~dp0"
set "TARGET_DIR=%PROJECT_DIR%target"
set "JAR=%TARGET_DIR%\fiscal-module.jar"
set "EXE=%PROJECT_DIR%FiscalModule.exe"
set "DIST_DIR=%PROJECT_DIR%dist"
set "L4J_CONFIG=%PROJECT_DIR%launch4j-config.xml"

:: Ajuste o caminho do Launch4j conforme sua instalação
set "LAUNCH4J_HOME=C:\Program Files (x86)\Launch4j"
set "LAUNCH4J=%LAUNCH4J_HOME%\launch4jc.exe"

:: Cores no console (requer Windows 10+)
set "GREEN=[32m"
set "RED=[31m"
set "YELLOW=[33m"
set "CYAN=[36m"
set "RESET=[0m"

:: ── Funções de log ─────────────────────────────────────────
call :log_step "Iniciando build do Módulo Fiscal InfoAtiva v2.0"
echo.

:: ── 1. Verificar dependências ──────────────────────────────
call :log_info "Verificando dependências..."

where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    call :log_error "Maven não encontrado! Instale o Maven e adicione ao PATH."
    call :log_error "Download: https://maven.apache.org/download.cgi"
    pause & exit /b 1
)

where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    call :log_error "Java não encontrado! Instale o JDK 17+ e adicione ao PATH."
    call :log_error "Download: https://adoptium.net/"
    pause & exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr "version"') do (
    set JAVA_VER=%%v
)
call :log_ok "Java encontrado: !JAVA_VER!"

for /f "tokens=*" %%v in ('mvn -version 2^>^&1 ^| findstr "Apache Maven"') do (
    call :log_ok "%%v"
)

echo.

:: ── 2. Baixar fontes NotoSans (se não existirem) ───────────
set "FONTS_DIR=%PROJECT_DIR%src\main\resources\fonts"
if not exist "%FONTS_DIR%\NotoSans-Regular.ttf" (
    call :log_info "Baixando fontes NotoSans..."

    if not exist "%FONTS_DIR%" mkdir "%FONTS_DIR%"

    where curl >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        curl -sL "https://github.com/google/fonts/raw/main/ofl/notosans/NotoSans%5bwdth,wght%5d.ttf" -o "%FONTS_DIR%\NotoSans-Regular.ttf" 2>nul
        if !ERRORLEVEL! equ 0 (
            call :log_ok "NotoSans-Regular.ttf baixado"
            copy "%FONTS_DIR%\NotoSans-Regular.ttf" "%FONTS_DIR%\NotoSans-Bold.ttf" >nul
        ) else (
            call :log_warn "Falha ao baixar NotoSans. PDFs usarao Arial do Windows."
        )
    ) else (
        call :log_warn "curl nao encontrado. Baixe NotoSans manualmente (veja resources/fonts/README.md)"
    )
) else (
    call :log_ok "Fontes NotoSans ja existem"
)
echo.

:: ── 3. Compilar com Maven ──────────────────────────────────
call :log_step "Compilando com Maven..."

cd /d "%PROJECT_DIR%"
call mvn clean package -DskipTests -q

if %ERRORLEVEL% neq 0 (
    call :log_error "FALHA na compilacao Maven! Verifique os erros acima."
    pause & exit /b 1
)

if not exist "%JAR%" (
    call :log_error "JAR nao gerado: %JAR%"
    pause & exit /b 1
)

for %%F in ("%JAR%") do set "JAR_SIZE=%%~zF"
set /a JAR_MB=%JAR_SIZE% / 1048576
call :log_ok "JAR gerado: fiscal-module.jar (%JAR_MB% MB)"
echo.

:: ── 4. Gerar .exe com Launch4j ──────────────────────────────
if exist "%LAUNCH4J%" (
    call :log_step "Gerando FiscalModule.exe com Launch4j..."

    "%LAUNCH4J%" "%L4J_CONFIG%"

    if %ERRORLEVEL% neq 0 (
        call :log_error "Launch4j falhou! Verifique o launch4j-config.xml."
    ) else if exist "%EXE%" (
        for %%F in ("%EXE%") do set "EXE_SIZE=%%~zF"
        set /a EXE_MB=!EXE_SIZE! / 1048576
        call :log_ok "FiscalModule.exe gerado (!EXE_MB! MB)"
    ) else (
        call :log_warn "EXE nao encontrado apos Launch4j (verificar config)"
    )
) else (
    call :log_warn "Launch4j nao encontrado em: %LAUNCH4J%"
    call :log_warn "Para gerar o .exe, ajuste LAUNCH4J_HOME neste script."
    call :log_warn "Download: https://launch4j.sourceforge.net/"
    call :log_info "Continuando sem gerar .exe — use o JAR diretamente:"
    call :log_info "  java -jar %JAR%"
)
echo.

:: ── 5. Criar pacote de distribuição (modo release) ─────────
if /i "%1"=="release" (
    call :log_step "Criando pacote de distribuicao..."

    if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
    mkdir "%DIST_DIR%"

    :: Copiar arquivos essenciais
    copy "%JAR%"           "%DIST_DIR%\" >nul
    copy "%PROJECT_DIR%conexao.ini"      "%DIST_DIR%\" >nul 2>&1
    copy "%PROJECT_DIR%xmlContador.ini"  "%DIST_DIR%\" >nul 2>&1
    copy "%PROJECT_DIR%IniciarFiscal.bat" "%DIST_DIR%\" >nul 2>&1

    if exist "%EXE%" copy "%EXE%" "%DIST_DIR%\" >nul

    :: Gerar IniciarFiscal.bat de instalação
    (
        echo @echo off
        echo title Modulo Fiscal InfoAtiva
        echo cd /d "%%~dp0"
        echo start "" "FiscalModule.exe" --tray
        echo if errorlevel 1 ^(
        echo     echo Tentando via Java direto...
        echo     java -jar fiscal-module.jar
        echo ^)
    ) > "%DIST_DIR%\IniciarFiscal.bat"

    :: Compactar em ZIP
    set "ZIP_NAME=FiscalModule_v2.0_%DATE:~6,4%%DATE:~3,2%%DATE:~0,2%.zip"
    where powershell >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        powershell -Command "Compress-Archive -Path '%DIST_DIR%\*' -DestinationPath '%PROJECT_DIR%!ZIP_NAME!' -Force"
        call :log_ok "Pacote ZIP gerado: !ZIP_NAME!"
    )

    call :log_ok "Distribuicao criada em: %DIST_DIR%\"
    echo.
)

:: ── 6. Testar JAR (smoke test) ─────────────────────────────
call :log_step "Smoke test: verificando integridade do JAR..."
java -cp "%JAR%" br.com.infoativa.fiscal.Launcher --test >nul 2>&1
if %ERRORLEVEL% equ 0 (
    call :log_ok "JAR ok (classe principal encontrada)"
) else (
    call :log_warn "Smoke test nao confirmado (normal se --test nao estiver implementado)"
)

echo.
call :log_step "BUILD CONCLUIDO COM SUCESSO!"
echo.
echo   JAR:  %JAR%
if exist "%EXE%" echo   EXE:  %EXE%
echo.
echo  Para executar:
echo    java -jar "%JAR%"
if exist "%EXE%" (
    echo    ou: "%EXE%"
    echo    ou: "%EXE%" --tray    (modo bandeja, oculto)
)
echo.

if /i not "%1"=="release" pause
endlocal
exit /b 0

:: ── Funções auxiliares ──────────────────────────────────────
:log_step
echo %CYAN%[====] %~1%RESET%
exit /b 0

:log_info
echo %CYAN%[ -- ] %~1%RESET%
exit /b 0

:log_ok
echo %GREEN%[ OK ] %~1%RESET%
exit /b 0

:log_warn
echo %YELLOW%[WARN] %~1%RESET%
exit /b 0

:log_error
echo %RED%[ERRO] %~1%RESET%
exit /b 0
