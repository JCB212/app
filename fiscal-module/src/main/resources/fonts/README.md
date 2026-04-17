# Instalação das Fontes NotoSans (PDFs Unicode)

## Por que NotoSans?
A fonte padrão do PDFBox (`PDType1Font.HELVETICA`) não suporta acentos nem caracteres especiais.
O sistema tenta carregar automaticamente nesta ordem:
1. `NotoSans-Regular.ttf` / `NotoSans-Bold.ttf` em `resources/fonts/`
2. `C:\Windows\Fonts\arial.ttf` (Windows)
3. Fallback Helvetica (sem acentos)

## Como instalar NotoSans

### Opção 1 — Download manual (recomendado)
1. Acesse: https://fonts.google.com/noto/specimen/Noto+Sans
2. Clique em "Download family"
3. Extraia e copie os arquivos:
   - `NotoSans-Regular.ttf`
   - `NotoSans-Bold.ttf`
4. Cole em: `fiscal-module/src/main/resources/fonts/`
5. Execute: `mvn clean package`

### Opção 2 — Script PowerShell
```powershell
$dir = "src/main/resources/fonts"
New-Item -ItemType Directory -Force -Path $dir
Invoke-WebRequest `
  "https://github.com/google/fonts/raw/main/ofl/notosans/NotoSans-Regular.ttf" `
  -OutFile "$dir/NotoSans-Regular.ttf"
Invoke-WebRequest `
  "https://github.com/google/fonts/raw/main/ofl/notosans/NotoSans-Bold.ttf" `
  -OutFile "$dir/NotoSans-Bold.ttf"
```

### Opção 3 — Usar Arial do Windows (automático)
Sem nenhuma instalação — o sistema detecta `C:\Windows\Fonts\arial.ttf` automaticamente.
Funciona em 99% dos computadores Windows.

## Compilar após instalar
```bash
cd fiscal-module
mvn clean package
```

O `pom.xml` já inclui as fontes no JAR automaticamente via `<includes>**/*.ttf</includes>`.
