# PRD - Modulo Fechamento Fiscal v1.4

## v1.4 (16/04/2026)

### CORRECOES CRITICAS
- [x] **Conexao Firebird DEFINITIVA**: URL agora `jdbc:firebirdsql://HOST:PORTA/CAMINHO` com Properties (encoding=UTF8, sqlDialect=3). Sem WIN1252 que causava erro
- [x] **Filtro XML por data de emissao REAL**: Removido pre-filtro por data de arquivo. Agora le dhEmi de CADA XML individualmente. Se pedir Janeiro, so vem XMLs de Janeiro
- [x] **Layout dark futurista**: Tema completo #0a0e17/#0d1117/#161b22 com acentos azul #388bfd e verde #3fb950. Textos sempre legiveis

### NOVOS RECURSOS
- [x] **NFE_ITENS repository**: Dominio NfeItemRegistro + NfeItemRepository com descoberta dinamica de colunas e fallback se tabela nao existir
- [x] **Windows Task Scheduler**: WindowsSchedulerService cria/remove tarefas SCHTASKS nativas. Botoes na UI de Configuracoes
- [x] **45 classes Java** (5233 linhas) + 536 linhas CSS dark theme
- [x] FiscalModule.exe (21MB) + FiscalModule.jar (20MB)
- [x] 7 testes unitarios passando

## Backlog
- P1: Integrar NFE_ITENS nos SPEDs (C170 detalhado com itens reais)
- P2: PDFs completos com itens discriminados
- P2: SINTEGRA atualizado
