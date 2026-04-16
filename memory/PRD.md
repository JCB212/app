# PRD - Modulo de Fechamento Fiscal Automatico v1.2

## Problem Statement
Aplicacao Java desktop (JAR/EXE) para Fechamento Fiscal Automatico integrando com ERP Firebird 2.5.9.

## What's Been Implemented (16/04/2026)

### v1.2 (Atualização Atual)
- [x] **BUG FIX CRITICO**: Corrigida string de conexao Firebird de `host:port/path` para `host/port:path`
- [x] **Config DB editavel**: Tela de configuracoes agora permite editar IP, Porta e caminho da base .FDB com botao de browse e teste de conexao
- [x] **Dashboard com graficos**: BarChart vendas NFCe/NFe (6 meses), PieChart impostos, LineChart compras
- [x] **Agendamento envio**: Configuracao de dia do envio automatico (1-10) e dia limite com aviso de multa
- [x] **Email tecnico**: Campo para email do tecnico para receber relatorios de contingencia
- [x] **Modo anual corrigido**: Mes oculto quando "Anual" selecionado, gera pastas separadas por mes
- [x] **Cores corrigidas**: Labels, checkboxes, radio buttons e combos com texto escuro #1e293b em fundo claro
- [x] **Deteccao empresa melhorada**: Busca em EMITENTE, EMPRESA, CONFIGURACOES, CLIENTE
- [x] 42 classes Java (4453 linhas) + 525 linhas CSS
- [x] FiscalModule.exe (21MB) + FiscalModule.jar (20MB)
- [x] 7 testes unitarios passando
- [x] README completo com 362 linhas (passo a passo de instalacao)

## Backlog
- P1: Implementar SPED completo baseado nos arquivos de referencia fornecidos
- P1: Tabela NFE_ITENS para SPED detalhado
- P2: Verificacao automatica de contingencia em NFCe (modelo 65)
- P2: Agendamento com Timer que abre app automaticamente
- P3: Assinatura digital do .EXE
