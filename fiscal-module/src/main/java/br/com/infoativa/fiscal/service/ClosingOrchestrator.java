package br.com.infoativa.fiscal.service;

import br.com.infoativa.fiscal.config.AppConfig;
import br.com.infoativa.fiscal.db.DatabaseGateway;
import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.fiscal.*;
import br.com.infoativa.fiscal.mail.EmailService;
import br.com.infoativa.fiscal.report.*;
import br.com.infoativa.fiscal.xml.XmlScanService;
import br.com.infoativa.fiscal.zip.ZipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ClosingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ClosingOrchestrator.class);
    private final AppConfig config;
    private final DatabaseGateway dbGateway;

    public ClosingOrchestrator(AppConfig config, DatabaseGateway dbGateway) {
        this.config = config;
        this.dbGateway = dbGateway;
    }

    public ProcessamentoResult execute(Periodo periodo, boolean enviarEmail, Consumer<String> progress) {
        try {
            progress.accept("Iniciando processamento: " + periodo.descricao());

            // 1. Create output structure
            String baseName = periodo.mesAnoRef();
            Path outputDir = OutputStructureService.createStructure(baseName);
            progress.accept("Estrutura de saida criada");

            // 2. Scan and organize XMLs
            XmlScanService scanner = new XmlScanService();
            List<XmlDocumentInfo> allXmls = new ArrayList<>();

            progress.accept("Escaneando XMLs de NFe...");
            allXmls.addAll(scanner.scan(config.caminhoXmlNfe(), periodo, progress));

            progress.accept("Escaneando XMLs de NFCe...");
            allXmls.addAll(scanner.scan(config.caminhoXmlNfce(), periodo, progress));

            progress.accept("Escaneando XMLs de Compras...");
            allXmls.addAll(scanner.scan(config.caminhoXmlCompras(), periodo, progress));

            progress.accept("Total de XMLs encontrados: " + allXmls.size());

            // 3. Copy XMLs to organized structure
            scanner.copyToOutput(allXmls, outputDir);
            progress.accept("XMLs organizados nas pastas");

            int xmlsNfe = (int) allXmls.stream().filter(XmlDocumentInfo::isNfe).count();
            int xmlsNfce = (int) allXmls.stream().filter(XmlDocumentInfo::isNfce).count();
            int xmlsCompras = allXmls.size() - xmlsNfe - xmlsNfce;

            // 4. Generate fiscal obligations from DB
            Connection conn = dbGateway.getConnection();

            List<FiscalObligationStrategy> strategies = List.of(
                new SpedFiscalStrategy(),
                new SpedContribuicoesStrategy(),
                new SintegraStrategy()
            );

            for (FiscalObligationStrategy strategy : strategies) {
                try {
                    progress.accept("Gerando " + strategy.name() + "...");
                    strategy.generate(conn, periodo, outputDir);
                    progress.accept(strategy.name() + " gerado com sucesso");
                } catch (Exception e) {
                    log.error("Erro ao gerar {}: {}", strategy.name(), e.getMessage());
                    progress.accept("ERRO ao gerar " + strategy.name() + ": " + e.getMessage());
                }
            }

            // 5. Generate PDF reports (including new ones)
            PdfReportService pdfService = new PdfReportService();
            SequenciaReportService sequenciaService = new SequenciaReportService();
            CstCfopReportService cstCfopService = new CstCfopReportService();
            MonofasicoReportService monoService = new MonofasicoReportService();
            DevolucoesReportService devService = new DevolucoesReportService();

            try {
                progress.accept("Gerando PDF - Resumo de Vendas...");
                pdfService.gerarResumoVendas(conn, periodo, outputDir);

                progress.accept("Gerando PDF - Resumo de Impostos...");
                pdfService.gerarResumoImpostos(conn, periodo, outputDir);

                progress.accept("Gerando PDF - Resumo de Compras...");
                pdfService.gerarResumoCompras(conn, periodo, outputDir);

                progress.accept("Gerando PDF - Sequencias...");
                sequenciaService.gerar(conn, periodo, outputDir);

                progress.accept("Gerando PDF - CST/CFOP...");
                cstCfopService.gerar(conn, periodo, outputDir);

                progress.accept("Gerando PDF - Monofasicos...");
                monoService.gerar(conn, periodo, outputDir);

                progress.accept("Gerando PDF - Devolucoes...");
                devService.gerar(conn, periodo, outputDir);

                progress.accept("Todos os PDFs gerados com sucesso");
            } catch (Exception e) {
                log.error("Erro ao gerar PDFs: {}", e.getMessage());
                progress.accept("ERRO ao gerar PDFs: " + e.getMessage());
            }

            // 6. Create ZIP
            progress.accept("Criando arquivo ZIP...");
            Path zipFile = OutputStructureService.getXmlContabilidadeRoot()
                .resolve("Fechamento_" + baseName + ".zip");
            ZipService.zipDirectory(outputDir, zipFile);
            progress.accept("ZIP criado: " + zipFile.getFileName());

            // 7. Send email
            boolean emailEnviado = false;
            if (enviarEmail) {
                progress.accept("Enviando email...");
                emailEnviado = EmailService.sendZip(
                    config.emailConfig(), config.emailDestinatarios(),
                    zipFile, periodo.descricao()
                );
                progress.accept(emailEnviado ? "Email enviado com sucesso!" : "ERRO ao enviar email");
            }

            String msg = String.format("Processamento concluido! %d XMLs (%d NFe, %d NFCe, %d Compras) + 7 PDFs + 3 TXT",
                allXmls.size(), xmlsNfe, xmlsNfce, xmlsCompras);
            progress.accept(msg);

            return new ProcessamentoResult(allXmls.size(), xmlsNfe, xmlsNfce, xmlsCompras,
                outputDir, zipFile, emailEnviado, msg);

        } catch (Exception e) {
            log.error("Erro no processamento: {}", e.getMessage(), e);
            progress.accept("ERRO: " + e.getMessage());
            return new ProcessamentoResult(0, 0, 0, 0, null, null, false,
                "Erro: " + e.getMessage());
        }
    }

    public ProcessamentoResult executeAnual(int ano, boolean enviarEmail, Consumer<String> progress) {
        List<Periodo> periodos = PeriodService.resolveAnual(ano);
        ProcessamentoResult lastResult = null;
        int totalXmls = 0;

        for (Periodo p : periodos) {
            progress.accept("=== Processando " + PeriodService.nomeMes(p.inicio().getMonthValue()) + "/" + ano + " ===");
            lastResult = execute(p, false, progress);
            totalXmls += lastResult.totalXmlsProcessados();
        }

        if (enviarEmail && lastResult != null && lastResult.arquivoZip() != null) {
            progress.accept("Enviando email com fechamento anual...");
            boolean sent = EmailService.sendZip(config.emailConfig(), config.emailDestinatarios(),
                lastResult.arquivoZip(), "Anual " + ano);
            progress.accept(sent ? "Email enviado!" : "Erro ao enviar email");
        }

        progress.accept("=== Processamento anual concluido! Total XMLs: " + totalXmls + " ===");
        return lastResult;
    }
}
