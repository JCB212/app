# PRD - Modulo de Fechamento Fiscal Automatico

## Problem Statement
Construir uma aplicacao Java desktop (JAR/EXE) para Fechamento Fiscal Automatico que integra com ERP Firebird 2.5.9. O sistema deve extrair XMLs, gerar obrigacoes fiscais (SPED/SINTEGRA), criar PDFs de resumo e enviar automaticamente ao contador por email.

## Architecture
- **Linguagem**: Java 17+ com Records, NIO, StAX
- **UI**: JavaFX 17 com CSS customizado (design moderno)
- **Banco**: Firebird 2.5.9 via Jaybird 4.0.10 JDBC
- **Email**: Jakarta Mail 2.0.1 (SMTP SSL porta 465)
- **PDF**: Apache PDFBox 2.0.31
- **ZIP**: Apache Commons Compress 1.26.2
- **Config**: ini4j 0.5.4
- **Logs**: SLF4J + Logback
- **Build**: Maven com Shade Plugin (fat JAR multiplataforma)

## User Persona
- Administrador fiscal de empresa brasileira
- Utiliza ERP com Firebird 2.5.9
- Precisa enviar fechamento mensal ao contador

## Core Requirements
1. Leitura dinamica de conexao.ini e xmlContador.ini
2. Filtro de XMLs por periodo (otimizado para 100k+ arquivos)
3. Geracao de SPED Fiscal, SPED Contribuicoes e SINTEGRA
4. PDFs: Resumo de Vendas, Impostos e Compras
5. ZIP de todos os arquivos gerados
6. Envio automatico por email SMTP
7. Interface JavaFX moderna com sidebar navigation
8. Senha de acesso opcional

## What's Been Implemented (16/04/2026)
- [x] Estrutura Maven completa com todas dependencias
- [x] 29 classes Java (2958 linhas)
- [x] Interface JavaFX moderna com 5 telas (Home, Processar, Destinatarios, Email, Config)
- [x] CSS customizado (395 linhas) - tema dark sidebar + light content
- [x] Conexao Firebird via Jaybird (conexao.ini)
- [x] Repositorios: NFCe (82 colunas), NFe (133 colunas), NOTA_COMPRA_DETALHE (131 colunas)
- [x] Scanner XML com FileVisitor otimizado para 100k+ arquivos
- [x] Extrator de metadados XML via StAX (NFe/NFCe/Cancelados/Inutilizados)
- [x] Geracao SPED Fiscal (Blocos 0, C, E, H, 9)
- [x] Geracao SPED Contribuicoes (Blocos 0, A, C, D, F, M, 9)
- [x] Geracao SINTEGRA (Registros 10, 11, 50, 60M, 90, 99)
- [x] PDFs: Resumo Vendas, Impostos e Compras (PDFBox)
- [x] Servico ZIP (Commons Compress)
- [x] Servico Email (Jakarta Mail SSL)
- [x] Orquestrador de fechamento fiscal
- [x] Modos: Automatico, Manual, Anual
- [x] Config pre-configurada: mail.infoativa.com.br:465
- [x] Destinatario padrao: jean.carlos@infoativa.com.br
- [x] JAR executavel multiplataforma (20MB)
- [x] 7 testes unitarios passando
- [x] Log em sistema_fiscal.log

## Backlog
- P0: Nenhum (MVP completo)
- P1: Relatorio de Sequencias (gaps de numeracao NFe/NFCe)
- P1: Relatorio CST/CFOP discriminado
- P1: Relatorio de Monofasicos
- P1: Relatorio de Devolucoes
- P2: JasperReports para PDFs mais elaborados
- P2: Agendamento automatico de envio (cron/scheduler)
- P2: Dashboard com graficos de vendas/impostos
- P2: Exportacao para Excel (Apache POI)
- P3: Wrapper .EXE (Launch4j ou jpackage)

## Next Tasks
1. Testar com banco Firebird real e XMLs reais
2. Implementar relatorios de Sequencias e CST/CFOP
3. Criar wrapper .EXE com Launch4j para facilitar distribuicao
