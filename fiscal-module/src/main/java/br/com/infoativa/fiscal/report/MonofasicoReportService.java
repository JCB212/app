package br.com.infoativa.fiscal.report;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.NfceItemRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MonofasicoReportService {

    private static final Logger log = LoggerFactory.getLogger(MonofasicoReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 40;
    private static final float LH = 12;

    public void gerar(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        NfceItemRepository itemRepo = new NfceItemRepository(conn);
        List<NfceItemRegistro> monofasicos = itemRepo.findMonofasicos(periodo);

        BigDecimal totalItens = monofasicos.stream().map(NfceItemRegistro::totalItem).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMonoRet = monofasicos.stream().map(NfceItemRegistro::icmsMonoRetValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIcms = monofasicos.stream().map(NfceItemRegistro::icms).reduce(BigDecimal.ZERO, BigDecimal::add);

        Path pdfPath = outputDir.resolve("PDF").resolve("Monofasicos_" + periodo.mesAnoRef() + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                y = text(cs, "RELATORIO DE MONOFASICOS", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = text(cs, "Periodo: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT), MARGIN, y);
                y = text(cs, "Itens com tributacao monofasica (CST 02, 04, 15 ou CSOSN 500)", MARGIN, y);
                y -= LH * 2;

                // Summary
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                y = text(cs, "RESUMO", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = text(cs, "Total de itens monofasicos: " + monofasicos.size(), MARGIN + 20, y);
                y = text(cs, "Valor total dos itens: R$ " + money(totalItens), MARGIN + 20, y);
                y = text(cs, "Total ICMS retido monofasico: R$ " + money(totalMonoRet), MARGIN + 20, y);
                y = text(cs, "Total ICMS normal: R$ " + money(totalIcms), MARGIN + 20, y);
                y -= LH * 2;

                // Detail table
                if (!monofasicos.isEmpty()) {
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                    y = text(cs, "DETALHAMENTO", MARGIN, y);
                    y -= 4;

                    cs.setFont(PDType1Font.HELVETICA_BOLD, 7);
                    y = text(cs, String.format("%-6s %-6s %-6s %10s %10s %10s %10s %10s",
                        "GTIN", "CST", "CSOSN", "Vlr Item", "Base ICMS", "ICMS", "Mono Ret", "Taxa Mono"), MARGIN + 10, y);

                    cs.setFont(PDType1Font.HELVETICA, 7);
                    int count = 0;
                    for (NfceItemRegistro item : monofasicos) {
                        if (count++ > 45) {
                            y = text(cs, "... mais " + (monofasicos.size() - 45) + " itens", MARGIN + 10, y);
                            break;
                        }
                        y = text(cs, String.format("%-6s %-6s %-6s %10s %10s %10s %10s %10s",
                            trunc(item.gtin(), 6), trunc(item.cst(), 6), trunc(item.csosn(), 6),
                            money(item.totalItem()), money(item.baseIcms()),
                            money(item.icms()), money(item.icmsMonoRetValor()),
                            money(item.icmsMonoRetTaxa())), MARGIN + 10, y);
                        if (y < MARGIN + 40) break;
                    }
                } else {
                    cs.setFont(PDType1Font.HELVETICA, 10);
                    y = text(cs, "Nenhum item monofasico encontrado no periodo.", MARGIN + 20, y);
                }
            }
            doc.save(pdfPath.toFile());
        }
        log.info("PDF Monofasicos gerado: {}", pdfPath);
    }

    private float text(PDPageContentStream cs, String t, float x, float y) throws IOException {
        cs.beginText(); cs.newLineAtOffset(x, y); cs.showText(t); cs.endText();
        return y - LH;
    }
    private String money(BigDecimal v) {
        if (v == null) return "0,00";
        return String.format("%,.2f", v).replace(",", "X").replace(".", ",").replace("X", ".");
    }
    private String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
