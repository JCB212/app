# PRD - Modulo de Fechamento Fiscal Automatico v1.3

## What's Been Implemented (16/04/2026)

### v1.3 (Atualização Atual - SPED Completo)
- [x] **SPED Fiscal reescrito** baseado no arquivo real do cliente (Leiaute 019)
  - Blocos: 0000,0001,0005,0100,0150,0190,0200,0400,0990,B,C001,C100,C170,C190,C405,C420,C490,C990,D,E001,E100,E110,E990,G,H,K,1001,1010,1601,1990,9
  - Participantes (0150), Produtos (0200), Unidades (0190)
  - NFe entradas com C170 itens e C190 analitico
  - NFCe resumo diario com C405/C420/C490
- [x] **SPED Contribuicoes reescrito** (Leiaute v020 para 2026, auto-calcula versao por ano)
  - C100/C170 para NFe entradas com PIS/COFINS detalhado
  - C175 para NFCe vendas consolidado por CFOP
  - M200/M400/M410/M600/M800/M810 apuracao
  - Plano de contas (0500)
- [x] **Verificacao de contingencia** em cada XML:
  - Detecta tpEmis != 1 (FS-IA, SCAN, DPEC, FS-DA, SVC-AN, SVC-RS, OFFLINE-NFCe)
  - Compara dhEmi vs dhRecbto (discrepancia > 1h)
  - Pasta separada "Contingencia" no output
- [x] **Config DB editavel** na UI com browse .FDB e teste de conexao
- [x] **Agendamento envio** (dia 1-10 + dia limite com aviso multa SEFAZ)
- [x] **Email tecnico** para receber relatorio de contingencias
- [x] **Modo anual** oculta seletor de mes
- [x] **Cores corrigidas** - texto escuro em fundo claro
- [x] 42 classes Java (4811 linhas) + 525 linhas CSS
- [x] FiscalModule.exe (21MB) + FiscalModule.jar (20MB)
- [x] 7 testes unitarios passando

## Backlog
- P1: Criar tabela NFE_ITENS repository (acessar base real)
- P1: SINTEGRA atualizado com formato correto
- P2: PDFs mais detalhados com todos os itens
- P2: Timer nativo Windows para auto-abertura
- P3: Assinatura digital do .EXE
