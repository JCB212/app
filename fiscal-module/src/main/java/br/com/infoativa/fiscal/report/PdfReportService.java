package br.com.infoativa.fiscal.report;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.CompraRepository;
import br.com.infoativa.fiscal.repository.NfceRepository;
import br.com.infoativa.fiscal.repository.NfeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PdfReportService {

    private static final Logger log = LoggerFactory.getLogger(PdfReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 40;
    private static final float LINE_HEIGHT = 14;

    public void gerarResumoVendas(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);

        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);

        BigDecimal totalNfce = nfces.stream()
            .filter(n -> !"S".equals(n.cupomCancelado()))
            .map(NfceRegistro::valorFinal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNfe = nfes.stream()
            .filter(n -> !"S".equals(n.cancelado()))
            .map(NfeRegistro::valorFinal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int qtdNfce = (int) nfces.stream().filter(n -> !"S".equals(n.cupomCancelado())).count();
        int qtdNfe = (int) nfes.stream().filter(n -> !"S".equals(n.cancelado())).count();
        int cancelados = (int) nfces.stream().filter(n -> "S".equals(n.cupomCancelado())).count()
            + (int) nfes.stream().filter(n -> "S".equals(n.cancelado())).count();

        Path pdfPath = outputDir.resolve("PDF").resolve("Resumo_Vendas_" + periodo.mesAnoRef() + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;
                float w = page.getMediaBox().getWidth();

                // Title
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                y = drawText(cs, "RESUMO DE VENDAS", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = drawText(cs, "Periodo: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT), MARGIN, y);
                y = drawText(cs, "Gerado em: " + LocalDate.now().format(FMT), MARGIN, y);
                y -= LINE_HEIGHT;

                // Separator
                cs.moveTo(MARGIN, y);
                cs.lineTo(w - MARGIN, y);
                cs.stroke();
                y -= LINE_HEIGHT * 1.5f;

                // Summary
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                y = drawText(cs, "RESUMO GERAL", MARGIN, y);
                y -= 4;
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = drawText(cs, "Quantidade de NFCe emitidas: " + qtdNfce, MARGIN + 20, y);
                y = drawText(cs, "Quantidade de NFe emitidas: " + qtdNfe, MARGIN + 20, y);
                y = drawText(cs, "Documentos cancelados: " + cancelados, MARGIN + 20, y);
                y -= LINE_HEIGHT;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                y = drawText(cs, "VALORES", MARGIN, y);
                y -= 4;
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = drawText(cs, "Total Vendas NFCe: R$ " + fmtMoney(totalNfce), MARGIN + 20, y);
                y = drawText(cs, "Total Vendas NFe:  R$ " + fmtMoney(totalNfe), MARGIN + 20, y);
                y -= LINE_HEIGHT;
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                y = drawText(cs, "TOTAL GERAL: R$ " + fmtMoney(totalNfce.add(totalNfe)), MARGIN + 20, y);

                // NFe table
                if (!nfes.isEmpty()) {
                    y -= LINE_HEIGHT * 2;
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                    y = drawText(cs, "DETALHAMENTO NFe", MARGIN, y);
                    y -= 4;
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 8);
                    y = drawText(cs, String.format("%-8s %-12s %-15s %-15s %-12s %-10s",
                        "Numero", "Data", "Chave", "Valor", "ICMS", "Status"), MARGIN + 10, y);
                    cs.setFont(PDType1Font.HELVETICA, 8);
                    int count = 0;
                    for (NfeRegistro nfe : nfes) {
                        if (count++ > 30) { y = drawText(cs, "... mais " + (nfes.size() - 30) + " registros", MARGIN + 10, y); break; }
                        y = drawText(cs, String.format("%-8d %-12s %-15s R$%-12s R$%-9s %-10s",
                            nfe.nfeNumero(),
                            nfe.nfeDataEmissao() != null ? nfe.nfeDataEmissao().format(FMT) : "",
                            safe(nfe.nfeChaveAcesso(), 15),
                            fmtMoney(nfe.valorFinal()),
                            fmtMoney(nfe.valorIcms()),
                            safe(nfe.nfeStatus(), 10)
                        ), MARGIN + 10, y);
                        if (y < MARGIN + 40) break;
                    }
                }
            }
            doc.save(pdfPath.toFile());
        }
        log.info("PDF Resumo de Vendas gerado: {}", pdfPath);
    }

    public void gerarResumoImpostos(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);

        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);

        BigDecimal icmsNfce = sum(nfces, NfceRegistro::icms);
        BigDecimal pisNfce = sum(nfces, NfceRegistro::pis);
        BigDecimal cofinsNfce = sum(nfces, NfceRegistro::cofins);
        BigDecimal icmsNfe = sumNfe(nfes, NfeRegistro::valorIcms);
        BigDecimal icmsStNfe = sumNfe(nfes, NfeRegistro::valorIcmsSt);
        BigDecimal pisNfe = sumNfe(nfes, NfeRegistro::valorPis);
        BigDecimal cofinsNfe = sumNfe(nfes, NfeRegistro::valorCofins);
        BigDecimal ipiNfe = sumNfe(nfes, NfeRegistro::valorIpi);

        Path pdfPath = outputDir.resolve("PDF").resolve("Resumo_Impostos_" + periodo.mesAnoRef() + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                y = drawText(cs, "RESUMO DE IMPOSTOS", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = drawText(cs, "Periodo: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT), MARGIN, y);
                y -= LINE_HEIGHT * 2;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                y = drawText(cs, "IMPOSTOS - NFCe (Vendas Consumidor)", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = drawText(cs, "ICMS:    R$ " + fmtMoney(icmsNfce), MARGIN + 20, y);
                y = drawText(cs, "PIS:     R$ " + fmtMoney(pisNfce), MARGIN + 20, y);
                y = drawText(cs, "COFINS:  R$ " + fmtMoney(cofinsNfce), MARGIN + 20, y);
                y -= LINE_HEIGHT;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                y = drawText(cs, "IMPOSTOS - NFe", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = drawText(cs, "ICMS:    R$ " + fmtMoney(icmsNfe), MARGIN + 20, y);
                y = drawText(cs, "ICMS ST: R$ " + fmtMoney(icmsStNfe), MARGIN + 20, y);
                y = drawText(cs, "PIS:     R$ " + fmtMoney(pisNfe), MARGIN + 20, y);
                y = drawText(cs, "COFINS:  R$ " + fmtMoney(cofinsNfe), MARGIN + 20, y);
                y = drawText(cs, "IPI:     R$ " + fmtMoney(ipiNfe), MARGIN + 20, y);
                y -= LINE_HEIGHT;

                BigDecimal totalImpostos = icmsNfce.add(pisNfce).add(cofinsNfce)
                    .add(icmsNfe).add(icmsStNfe).add(pisNfe).add(cofinsNfe).add(ipiNfe);
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                y = drawText(cs, "TOTAL IMPOSTOS: R$ " + fmtMoney(totalImpostos), MARGIN, y);
            }
            doc.save(pdfPath.toFile());
        }
        log.info("PDF Resumo de Impostos gerado: {}", pdfPath);
    }

    public void gerarResumoCompras(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        CompraRepository compraRepo = new CompraRepository(conn);
        List<CompraRegistro> compras = compraRepo.findByPeriodo(periodo);

        BigDecimal totalCompras = compras.stream().map(CompraRegistro::totalItem).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIcms = compras.stream().map(CompraRegistro::icmsValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPis = compras.stream().map(CompraRegistro::pisValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCofins = compras.stream().map(CompraRegistro::cofinsValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIpi = compras.stream().map(CompraRegistro::ipiValor).reduce(BigDecimal.ZERO, BigDecimal::add);

        Path pdfPath = outputDir.resolve("PDF").resolve("Resumo_Compras_" + periodo.mesAnoRef() + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                y = drawText(cs, "RESUMO DE COMPRAS (FORNECEDORES)", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = drawText(cs, "Periodo: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT), MARGIN, y);
                y -= LINE_HEIGHT * 2;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                y = drawText(cs, "RESUMO GERAL DE COMPRAS", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = drawText(cs, "Total de itens: " + compras.size(), MARGIN + 20, y);
                y = drawText(cs, "Valor Total: R$ " + fmtMoney(totalCompras), MARGIN + 20, y);
                y -= LINE_HEIGHT;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                y = drawText(cs, "IMPOSTOS DAS COMPRAS", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = drawText(cs, "ICMS:   R$ " + fmtMoney(totalIcms), MARGIN + 20, y);
                y = drawText(cs, "PIS:    R$ " + fmtMoney(totalPis), MARGIN + 20, y);
                y = drawText(cs, "COFINS: R$ " + fmtMoney(totalCofins), MARGIN + 20, y);
                y = drawText(cs, "IPI:    R$ " + fmtMoney(totalIpi), MARGIN + 20, y);
                y -= LINE_HEIGHT * 2;

                // Detalhamento
                cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                y = drawText(cs, "DETALHAMENTO POR ITEM", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA_BOLD, 7);
                y = drawText(cs, String.format("%-35s %-8s %-10s %-10s %-10s %-6s",
                    "Descricao", "NCM", "Qtd", "Vl.Unit", "Total", "CFOP"), MARGIN + 10, y);
                cs.setFont(PDType1Font.HELVETICA, 7);
                int count = 0;
                for (CompraRegistro c : compras) {
                    if (count++ > 40) {
                        y = drawText(cs, "... mais " + (compras.size() - 40) + " itens", MARGIN + 10, y);
                        break;
                    }
                    y = drawText(cs, String.format("%-35s %-8s %-10s R$%-8s R$%-8s %-6s",
                        safe(c.descricao(), 35), safe(c.ncm(), 8),
                        c.quantidade().toPlainString(),
                        fmtMoney(c.valorUnitario()), fmtMoney(c.totalItem()),
                        safe(c.cfop(), 6)
                    ), MARGIN + 10, y);
                    if (y < MARGIN + 40) break;
                }
            }
            doc.save(pdfPath.toFile());
        }
        log.info("PDF Resumo de Compras gerado: {}", pdfPath);
    }

    private float drawText(PDPageContentStream cs, String text, float x, float y) throws IOException {
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y - LINE_HEIGHT;
    }

    private String fmtMoney(BigDecimal v) {
        if (v == null) return "0,00";
        return String.format("%,.2f", v).replace(",", "X").replace(".", ",").replace("X", ".");
    }

    private String safe(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    @FunctionalInterface
    interface NfceField { BigDecimal get(NfceRegistro r); }
    @FunctionalInterface
    interface NfeField { BigDecimal get(NfeRegistro r); }

    private BigDecimal sum(List<NfceRegistro> list, NfceField f) {
        return list.stream().filter(n -> !"S".equals(n.cupomCancelado())).map(f::get).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal sumNfe(List<NfeRegistro> list, NfeField f) {
        return list.stream().filter(n -> !"S".equals(n.cancelado())).map(f::get).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
