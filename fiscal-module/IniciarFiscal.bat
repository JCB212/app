@echo off
title Modulo Fiscal - InfoAtiva
echo Iniciando Modulo Fiscal...
java -jar FiscalModule.jar %*
if errorlevel 1 (
    echo.
    echo ERRO: Java 17 ou superior nao encontrado!
    echo Baixe em: https://adoptium.net/
    echo.
    pause
)
