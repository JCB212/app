# PRD - Modulo de Fechamento Fiscal Automatico v1.1

## Problem Statement
Aplicacao Java desktop (JAR/EXE) para Fechamento Fiscal Automatico integrando com ERP Firebird 2.5.9. Extrai XMLs, gera obrigacoes fiscais (SPED/SINTEGRA), cria PDFs de resumo e envia automaticamente ao contador por email.

## Architecture
- **Linguagem**: Java 17+ com Records, NIO, StAX
- **UI**: JavaFX 17 com CSS customizado (design moderno com header de empresa)
- **Banco**: Firebird 2.5.9 via Jaybird 4.0.10 JDBC
- **Email**: Jakarta Mail 2.0.1 (SMTP SSL porta 465)
- **PDF**: Apache PDFBox 2.0.31
- **ZIP**: Apache Commons Compress 1.26.2
- **Config**: ini4j 0.5.4
- **Logs**: SLF4J + Logback
- **Build**: Maven com Shade Plugin (fat JAR) + Launch4j (.EXE)

## Database Tables Used
- **NFCE** (82 colunas) - Cupons fiscais eletronicos
- **NFCE_ITENS** (79 colunas) - Itens dos cupons
- **NFE** (133 colunas) - Notas fiscais eletronicas
- **NOTA_COMPRA** (50 colunas) - Cabecalho notas de compra
- **NOTA_COMPRA_DETALHE** (131 colunas) - Itens das compras
- **PARAMETROS** - Configuracoes do sistema ERP
- **USUARIO** - Login/autenticacao

## What's Been Implemented (16/04/2026)
### v1.0 (Build inicial)
- [x] 29 classes Java, interface JavaFX com 5 telas
- [x] Conexao Firebird, repositorios, SPED/SINTEGRA, 3 PDFs, ZIP, Email

### v1.1 (Atualização atual)
- [x] **42 classes Java** (3921 linhas) + 421 linhas CSS
- [x] **Correcao CompraRepository** - Join com NOTA_COMPRA ao inves de NFE
- [x] **Tabela PARAMETROS** - Leitura de configuracoes do ERP
- [x] **Tabela USUARIO** - Login com autenticacao via banco de dados
- [x] **Tabela NOTA_COMPRA** - Repositorio de cabecalho de compras
- [x] **Tabela NFCE_ITENS** - Repositorio de itens para relatorios detalhados
- [x] **Header da empresa** - Nome da empresa e usuario logado no topo
- [x] **Relatorio de Sequencias** - Detecta furos na numeracao NFe/NFCe
- [x] **Relatorio CST/CFOP** - Vendas e compras agrupadas por CST e CFOP
- [x] **Relatorio Monofasicos** - Itens com tributacao monofasica (CST 02,04,15/CSOSN 500)
- [x] **Relatorio Devolucoes** - Notas de devolucao por CFOP (1201,1202,5201,5202,etc.)
- [x] **FiscalModule.exe** gerado via Launch4j (wrapper Windows)
- [x] **FiscalModule.jar** fat JAR multiplataforma (20MB)
- [x] **IniciarFiscal.bat** script de inicializacao Windows
- [x] 7 testes unitarios passando
- [x] Todos os 7 PDFs gerados no processamento

## Arquivos de Distribuicao
- `FiscalModule.exe` - Executavel Windows (20MB)
- `FiscalModule.jar` - JAR executavel multiplataforma
- `IniciarFiscal.bat` - Script de inicializacao
- `conexao.ini` - Configuracao de conexao Firebird
- `xmlContador.ini` - Configuracao de caminhos/email

## Backlog
- P1: Tabela EMPRESA/EMITENTE para nome correto da empresa
- P2: Dashboard com graficos usando JavaFX Charts
- P2: Agendamento automatico (Timer/ScheduledExecutor)
- P2: Exportacao para Excel (Apache POI)
- P3: Assinatura digital do .EXE
