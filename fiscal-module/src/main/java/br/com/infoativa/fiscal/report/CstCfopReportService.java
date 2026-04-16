package br.com.infoativa.fiscal.report;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.*;
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
import java.util.*;
import java.util.stream.Collectors;

public class CstCfopReportService {

    private static final Logger log = LoggerFactory.getLogger(CstCfopReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 40;
    private static final float LH = 12;

    public void gerar(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        NfceItemRepository itemRepo = new NfceItemRepository(conn);
        CompraRepository compraRepo = new CompraRepository(conn);

        List<NfceItemRegistro> itensVenda = itemRepo.findByPeriodo(periodo);
        List<Object[]> comprasCstCfop = compraRepo.findGroupedByCstCfop(periodo);

        // Group vendas by CST + CFOP
        Map<String, List<NfceItemRegistro>> vendaGrupo = itensVenda.stream()
            .collect(Collectors.groupingBy(i -> (i.cst().isEmpty() ? i.csosn() : i.cst()) + "|" + i.cfop()));

        Path pdfPath = outputDir.resolve("PDF").resolve("CST_CFOP_" + periodo.mesAnoRef() + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                y = text(cs, "RELATORIO CST/CFOP", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = text(cs, "Periodo: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT), MARGIN, y);
                y -= LH * 2;

                // VENDAS (NFCe)
                cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
                y = text(cs, "VENDAS (NFCe) - Por CST/CSOSN e CFOP", MARGIN, y);
                y -= 4;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 8);
                y = text(cs, String.format("%-8s %-8s %8s %14s %12s %12s %12s",
                    "CST", "CFOP", "Qtd", "Valor Total", "ICMS", "PIS", "COFINS"), MARGIN + 10, y);
                y -= 2;

                cs.setFont(PDType1Font.HELVETICA, 8);
                BigDecimal totalVendas = BigDecimal.ZERO;
                for (var entry : new TreeMap<>(vendaGrupo).entrySet()) {
                    String[] parts = entry.getKey().split("\\|");
                    String cst = parts[0];
                    String cfop = parts.length > 1 ? parts[1] : "";
                    List<NfceItemRegistro> items = entry.getValue();

                    BigDecimal total = items.stream().map(NfceItemRegistro::totalItem).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal icms = items.stream().map(NfceItemRegistro::icms).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal pis = items.stream().map(NfceItemRegistro::pis).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal cofins = items.stream().map(NfceItemRegistro::cofins).reduce(BigDecimal.ZERO, BigDecimal::add);
                    totalVendas = totalVendas.add(total);

                    y = text(cs, String.format("%-8s %-8s %8d %14s %12s %12s %12s",
                        cst, cfop, items.size(), money(total), money(icms), money(pis), money(cofins)), MARGIN + 10, y);
                    if (y < MARGIN + 100) break;
                }

                cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
                y -= 4;
                y = text(cs, "Total Vendas: R$ " + money(totalVendas) + " (" + itensVenda.size() + " itens)", MARGIN + 10, y);
                y -= LH * 2;

                // COMPRAS
                cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
                y = text(cs, "COMPRAS - Por CST e CFOP", MARGIN, y);
                y -= 4;
                cs.setFont(PDType1Font.HELVETICA_BOLD, 8);
                y = text(cs, String.format("%-8s %-8s %8s %14s %12s %12s %12s",
                    "CST", "CFOP", "Qtd", "Valor Total", "ICMS", "PIS", "COFINS"), MARGIN + 10, y);
                y -= 2;

                cs.setFont(PDType1Font.HELVETICA, 8);
                BigDecimal totalCompras = BigDecimal.ZERO;
                for (Object[] row : comprasCstCfop) {
                    String cst = row[0] != null ? row[0].toString() : "";
                    String cfop = row[1] != null ? row[1].toString() : "";
                    int qtd = (int) row[2];
                    BigDecimal total = (BigDecimal) row[3];
                    BigDecimal icms = (BigDecimal) row[4];
                    BigDecimal pis = (BigDecimal) row[5];
                    BigDecimal cofins = (BigDecimal) row[6];
                    totalCompras = totalCompras.add(total != null ? total : BigDecimal.ZERO);

                    y = text(cs, String.format("%-8s %-8s %8d %14s %12s %12s %12s",
                        cst, cfop, qtd, money(total), money(icms), money(pis), money(cofins)), MARGIN + 10, y);
                    if (y < MARGIN + 40) break;
                }

                cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
                y -= 4;
                y = text(cs, "Total Compras: R$ " + money(totalCompras), MARGIN + 10, y);
            }
            doc.save(pdfPath.toFile());
        }
        log.info("PDF CST/CFOP gerado: {}", pdfPath);
    }

    private float text(PDPageContentStream cs, String t, float x, float y) throws IOException {
        cs.beginText(); cs.newLineAtOffset(x, y); cs.showText(t); cs.endText();
        return y - LH;
    }

    private String money(BigDecimal v) {
        if (v == null) return "0,00";
        return String.format("%,.2f", v).replace(",", "X").replace(".", ",").replace("X", ".");
    }
}
