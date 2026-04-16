# Modulo de Fechamento Fiscal Automatico - InfoAtiva

## Requisitos
- **Java 17+** instalado na maquina
- **Firebird 2.5.9** com o banco de dados do ERP
- Conexao de rede ao servidor de email (SMTP)

## Como Executar

### Windows
```
java -jar FiscalModule.jar
```

Ou clique duplo no arquivo `FiscalModule.jar` (se Java estiver associado a arquivos .jar)

### Para criar um atalho .bat
Crie um arquivo `IniciarFiscal.bat` com o conteudo:
```batch
@echo off
java -jar FiscalModule.jar
pause
```

## Configuracao

### conexao.ini
Deve estar na mesma pasta do JAR ou em `C:\TSD\Host\`:
```ini
[CONEXAO]
IP_SERVIDOR=127.0.0.1
PORTA=3050
BASEHOST=C:\TSD\Host\HOST.FDB
```

### xmlContador.ini
Criado automaticamente na primeira execucao com valores padrao.
Pode ser editado manualmente ou pela interface grafica.

## Funcionalidades

### Tela Principal
- **Gerar e Enviar**: Processa XMLs, gera SPED/SINTEGRA/PDFs e envia por email
- **Destinatarios**: Gerencia emails dos contadores
- **Config. Email**: Configura SMTP (pre-configurado para InfoAtiva)
- **Configuracoes**: Caminhos de XML, senha de acesso, info do banco

### Modos de Processamento
1. **Automatico**: Processa o mes anterior ao atual
2. **Manual**: Seleciona mes/ano especifico
3. **Anual**: Gera relatorios de todos os meses do ano

### Arquivos Gerados (pasta XMLContabilidade)
- `NFe/` - XMLs de NF-e autorizadas
- `NFCe/` - XMLs de NFC-e autorizadas
- `Compras/` - XMLs de notas de fornecedores
- `Cancelados/` - XMLs de documentos cancelados
- `Inutilizados/` - XMLs de documentos inutilizados
- `TXT/SPED_FISCAL_MM_YYYY.txt` - SPED Fiscal
- `TXT/SPED_CONTRIBUICOES_MM_YYYY.txt` - SPED Contribuicoes
- `TXT/SINTEGRA_MM_YYYY.txt` - SINTEGRA
- `PDF/Resumo_Vendas_MM_YYYY.pdf` - Resumo de vendas
- `PDF/Resumo_Impostos_MM_YYYY.pdf` - Resumo de impostos
- `PDF/Resumo_Compras_MM_YYYY.pdf` - Resumo de compras de fornecedores
- `Fechamento_MM_YYYY.zip` - Arquivo ZIP com tudo

### Email Automatico
Pre-configurado com:
- SMTP: mail.infoativa.com.br (porta 465 SSL)
- Email: fiscal@infoativa.com.br
- Destinatario: jean.carlos@infoativa.com.br

## Stack Tecnica
- Java 17 (Records, NIO, StAX)
- JavaFX 17 (Interface grafica)
- Jaybird 4.0.10 (JDBC Firebird 2.5.9)
- Jakarta Mail 2.0.1 (Email SMTP)
- Apache PDFBox 2.0.31 (Geracao de PDFs)
- Apache Commons Compress 1.26.2 (ZIP)
- ini4j 0.5.4 (Leitura de arquivos INI)
- SLF4J + Logback (Logs em sistema_fiscal.log)

## Logs
O sistema gera logs detalhados em `sistema_fiscal.log` no mesmo diretorio do JAR.
