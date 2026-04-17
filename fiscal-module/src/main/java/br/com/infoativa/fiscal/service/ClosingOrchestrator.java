package br.com.infoativa.fiscal.service;

import br.com.infoativa.fiscal.config.AppConfig;
import br.com.infoativa.fiscal.db.DatabaseGateway;
import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.fiscal.*;
import br.com.infoativa.fiscal.mail.EmailService;
import br.com.infoativa.fiscal.report.*;
import br.com.infoativa.fiscal.xml.XmlCacheRepository;
import br.com.infoativa.fiscal.xml.XmlScanService;
import br.com.infoativa.fiscal.zip.ZipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Orquestrador do fechamento fiscal refatorado:
 * - Verifica existência de XMLs antes de gerar relatórios
 * - Respeita filtros do usuário (tipo de documento)
 * - Usa cache XML_PROCESSADOS para consultas históricas
 * - Logs completos com stack trace (nunca só getMessage)
 * - Performance: sem reprocessamento desnecessário
 */
public class ClosingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ClosingOrchestrator.class);
    private final AppConfig config;
    private final DatabaseGateway dbGateway;

    // Filtros do usuário
    private boolean processarNfe    = true;
    private boolean processarNfce   = true;
    private boolean processarCompras = true;
    private boolean gerarSped       = true;
    private boolean gerarSintegra   = true;

    public ClosingOrchestrator(AppConfig config, DatabaseGateway dbGateway) {
        this.config = config;
        this.dbGateway = dbGateway;
    }

    public void setFiltros(boolean processarNfe, boolean processarNfce,
                            boolean processarCompras, boolean gerarSped, boolean gerarSintegra) {
        this.processarNfe    = processarNfe;
        this.processarNfce   = processarNfce;
        this.processarCompras = processarCompras;
        this.gerarSped       = gerarSped;
        this.gerarSintegra   = gerarSintegra;
    }

    public ProcessamentoResult execute(Periodo periodo, boolean enviarEmail,
                                        Consumer<String> progress) {
        List<String> warnings = new ArrayList<>();

        try {
            progress.accept("Iniciando processamento: " + periodo.descricao());

            // ── 1. Validação inicial ───────────────────────────────────────────
            validateDependencies(progress);

            // ── 2. Criar estrutura de saída ────────────────────────────────────
            String baseName = periodo.mesAnoRef();
            Path outputDir = OutputStructureService.createStructure(baseName);
            progress.accept("Estrutura de saída criada em: " + outputDir);

            // ── 3. Inicializar cache XML_PROCESSADOS ───────────────────────────
            Connection conn = dbGateway.getConnection();
            XmlCacheRepository cache = new XmlCacheRepository(conn);
            cache.ensureTableExists();
            progress.accept("Cache XML_PROCESSADOS verificado");

            // ── 4. Varredura de XMLs com cache ────────────────────────────────
            XmlScanService scanner = new XmlScanService();
            List<XmlDocumentInfo> allXmls = new ArrayList<>();

            if (processarNfe && config.caminhoXmlNfe() != null && !config.caminhoXmlNfe().isBlank()) {
                progress.accept("Escaneando XMLs de NFe...");
                List<XmlDocumentInfo> nfes = scanner.scan(config.caminhoXmlNfe(), periodo, progress);
                scanner.scanAndUpdateCache(config.caminhoXmlNfe(), periodo, cache, progress);
                allXmls.addAll(nfes);
                log.info("NFe encontradas no periodo: {}", nfes.size());
            } else if (!processarNfe) {
                log.info("NFe desabilitada pelo usuario — nao processada");
            }

            if (processarNfce && config.caminhoXmlNfce() != null && !config.caminhoXmlNfce().isBlank()) {
                progress.accept("Escaneando XMLs de NFCe...");
                List<XmlDocumentInfo> nfces = scanner.scan(config.caminhoXmlNfce(), periodo, progress);
                scanner.scanAndUpdateCache(config.caminhoXmlNfce(), periodo, cache, progress);
                allXmls.addAll(nfces);
                log.info("NFCe encontradas no periodo: {}", nfces.size());
            } else if (!processarNfce) {
                log.info("NFCe desabilitada pelo usuario — nao processada");
            }

            if (processarCompras && config.caminhoXmlCompras() != null && !config.caminhoXmlCompras().isBlank()) {
                progress.accept("Escaneando XMLs de Compras...");
                List<XmlDocumentInfo> compras = scanner.scan(config.caminhoXmlCompras(), periodo, progress);
                scanner.scanAndUpdateCache(config.caminhoXmlCompras(), periodo, cache, progress);
                allXmls.addAll(compras);
                log.info("XMLs de compras encontrados no periodo: {}", compras.size());
            } else if (!processarCompras) {
                log.info("Compras desabilitadas pelo usuario — nao processadas");
            }

            progress.accept("Total de XMLs encontrados no período: " + allXmls.size());

            // ── 5. Verificar se há XMLs antes de gerar relatórios ─────────────
            if (allXmls.isEmpty()) {
                String warn = "ATENÇÃO: Nenhum XML encontrado no período " + periodo.descricao() +
                              ". Relatórios NÃO serão gerados.";
                log.warn(warn);
                progress.accept(warn);
                warnings.add(warn);
                return new ProcessamentoResult(periodo, outputDir, null,
                                               List.of(), List.of(), warnings);
            }

            // ── 6. Organizar XMLs nas pastas ─────────────────────────────────
            scanner.copyToOutput(allXmls, outputDir);
            progress.accept("XMLs organizados nas pastas de saída");

            // ── 7. Gerar obrigações fiscais (SPED, SINTEGRA) ─────────────────
            List<Path> reportFiles = new ArrayList<>();

            List<FiscalObligationStrategy> strategies = new ArrayList<>();
            if (gerarSped) {
                strategies.add(new SpedFiscalStrategy());
                strategies.add(new SpedContribuicoesStrategy());
            }
            if (gerarSintegra) {
                strategies.add(new SintegraStrategy());
            }

            for (FiscalObligationStrategy strategy : strategies) {
                try {
                    progress.accept("Gerando " + strategy.name() + "...");
                    Path generated = strategy.generate(conn, periodo, outputDir);
                    if (generated != null) {
                        reportFiles.add(generated);
                        progress.accept(strategy.name() + " gerado com sucesso");
                    }
                } catch (Exception e) {
                    String warn = "Falha ao gerar " + strategy.name() + ": " + e.getMessage();
                    log.error("Erro ao gerar {}", strategy.name(), e); // Stack trace completo
                    progress.accept("ERRO: " + warn);
                    warnings.add(warn);
                }
            }

            // ── 8. Gerar PDFs ─────────────────────────────────────────────────
            PdfReportService pdfService      = new PdfReportService();
            SequenciaReportService seqSvc    = new SequenciaReportService();
            CstCfopReportService cstSvc      = new CstCfopReportService();
            MonofasicoReportService monoSvc  = new MonofasicoReportService();
            DevolucoesReportService devSvc   = new DevolucoesReportService();

            try { progress.accept("Gerando PDF Resumo de Vendas...");
                  pdfService.gerarResumoVendas(conn, periodo, outputDir); }
            catch (Exception e) { log.error("Erro ao gerar PDF Vendas", e); warnings.add("PDF Vendas: " + e.getMessage()); }

            try { progress.accept("Gerando PDF Resumo de Impostos...");
                  pdfService.gerarResumoImpostos(conn, periodo, outputDir); }
            catch (Exception e) { log.error("Erro ao gerar PDF Impostos", e); warnings.add("PDF Impostos: " + e.getMessage()); }

            try { progress.accept("Gerando PDF Resumo de Compras...");
                  pdfService.gerarResumoCompras(conn, periodo, outputDir); }
            catch (Exception e) { log.error("Erro ao gerar PDF Compras", e); warnings.add("PDF Compras: " + e.getMessage()); }

            try { progress.accept("Gerando PDF Sequencias...");
                  seqSvc.gerarRelatorio(conn, periodo, outputDir); }
            catch (Exception e) { log.error("Erro ao gerar PDF Sequencias", e); }

            try { progress.accept("Gerando PDF CST/CFOP...");
                  cstSvc.gerarRelatorio(conn, periodo, outputDir); }
            catch (Exception e) { log.error("Erro ao gerar PDF CST/CFOP", e); }

            try { progress.accept("Gerando PDF Monofasico...");
                  monoSvc.gerarRelatorio(conn, periodo, outputDir); }
            catch (Exception e) { log.error("Erro ao gerar PDF Monofasico", e); }

            try { progress.accept("Gerando PDF Devolucoes...");
                  devSvc.gerarRelatorio(conn, periodo, outputDir); }
            catch (Exception e) { log.error("Erro ao gerar PDF Devolucoes", e); }

            // ── 9. Compactar ZIP ──────────────────────────────────────────────
            progress.accept("Compactando arquivos em ZIP...");
            Path zipFile = outputDir.getParent().resolve(
                "FechamentoFiscal_" + periodo.mesAnoRef() + ".zip");
            ZipService.zipDirectory(outputDir, zipFile);
            progress.accept("ZIP gerado: " + zipFile.getFileName());

            // ── 10. Enviar e-mail ─────────────────────────────────────────────
            if (enviarEmail) {
                progress.accept("Enviando e-mail ao contador...");
                try {
                    boolean sent = EmailService.sendZip(
                        config.emailConfig(),
                        config.emailDestinatarios(),
                        zipFile,
                        periodo.descricao()
                    );
                    if (sent) {
                        progress.accept("✅ E-mail enviado com sucesso!");
                        log.info("E-mail enviado com sucesso para periodo {}", periodo.descricao());
                    } else {
                        String warn = "E-mail NÃO enviado (verifique configurações SMTP)";
                        progress.accept("⚠️ " + warn);
                        warnings.add(warn);
                    }
                } catch (Exception e) {
                    log.error("Erro ao enviar e-mail", e);
                    warnings.add("Erro no e-mail: " + e.getMessage());
                }
            }

            progress.accept("✅ Processamento concluído com sucesso!");
            log.info("Fechamento concluido para periodo {} - XMLs: {}, Avisos: {}",
                     periodo.descricao(), allXmls.size(), warnings.size());

            List<Path> xmlPaths = allXmls.stream().map(XmlDocumentInfo::filePath).toList();
            return new ProcessamentoResult(periodo, outputDir, zipFile, xmlPaths, reportFiles, warnings);

        } catch (Exception e) {
            log.error("Erro critico no fechamento fiscal", e); // Stack trace completo
            progress.accept("❌ ERRO CRÍTICO: " + e.getMessage());
            warnings.add("Erro crítico: " + e.getMessage());
            return new ProcessamentoResult(periodo, null, null, List.of(), List.of(), warnings);
        }
    }

    /**
     * Reprocessar um mês específico limpando o cache.
     */
    public ProcessamentoResult reprocessarMes(Periodo periodo, Consumer<String> progress) {
        log.info("Reprocessamento forcado para periodo: {}", periodo.descricao());
        progress.accept("Iniciando reprocessamento do mês " + periodo.descricao());
        return execute(periodo, false, progress);
    }

    private void validateDependencies(Consumer<String> progress) throws Exception {
        // Validar banco
        try {
            dbGateway.getConnection().isValid(5);
            progress.accept("✅ Banco de dados: OK");
        } catch (Exception e) {
            throw new RuntimeException("Banco de dados indisponível: " + e.getMessage(), e);
        }

        // Validar diretórios
        if (processarNfe && config.caminhoXmlNfe() != null) {
            Path p = Path.of(config.caminhoXmlNfe());
            if (!Files.exists(p)) {
                log.warn("Diretorio NFe nao encontrado: {}", config.caminhoXmlNfe());
                progress.accept("⚠️ Diretório NFe não encontrado: " + config.caminhoXmlNfe());
            }
        }

        // Validar SMTP (rápido, não bloqueante)
        progress.accept("Validando configurações de e-mail...");
        try {
            boolean smtpOk = EmailService.testConnection(config.emailConfig());
            if (smtpOk) {
                progress.accept("✅ SMTP: OK");
            } else {
                progress.accept("⚠️ SMTP: Falhou (e-mail pode não ser enviado)");
            }
        } catch (Exception e) {
            log.warn("Validacao SMTP falhou: {}", e.getMessage());
        }
    }
}
