# PRD - Modulo Fechamento Fiscal v1.5

## v1.5 (16/04/2026)

### NOVOS RECURSOS
- [x] **Monitor XML Tempo Real**: Tela dedicada com WatchService Java NIO que escuta a pasta de NFCe. Detecta contingencias (tpEmis!=1, diferenca dhEmi/dhRecbto >1h). Log ao vivo na UI, contador de contingencias, gera relatorio TXT, envia por email ao tecnico
- [x] **PDFs Completos com Itens**: Reescrito PdfReportService com sistema multipaginas (PdfWriter interno). Vendas com TODOS os cupons NFCe e notas NFe discriminados. Impostos por documento individual. Compras com cabecalho + itens detalhados
- [x] **NFE_ITENS Repository**: Dominio + repositorio com descoberta dinamica de colunas e fallback
- [x] **46 classes Java** (5697 linhas) + 536 linhas CSS dark theme
- [x] 7 menus: Inicio, Dashboard, Gerar e Enviar, Monitor XML, Destinatarios, Config Email, Configuracoes
- [x] FiscalModule.exe (21MB) + FiscalModule.jar (20MB)

## Backlog
- P1: Integrar NFE_ITENS nos SPEDs
- P2: SINTEGRA atualizado
- P2: Exportacao Excel
