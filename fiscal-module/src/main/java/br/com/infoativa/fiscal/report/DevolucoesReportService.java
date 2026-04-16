package br.com.infoativa.fiscal.report;

import br.com.infoativa.fiscal.domain.*;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DevolucoesReportService {

    private static final Logger log = LoggerFactory.getLogger(DevolucoesReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 40;
    private static final float LH = 12;

    public void gerar(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        // Devoluções: NFe com ENTRADA_SAIDA = '0' (entrada) que são devoluções
        // ou NFe com tipo_operacao contendo 'devol' ou CFOP de devolução (1201, 1202, 1410, 1411, 2201, 2202)
        List<NfeRegistro> nfes = new NfeRepository(conn).findByPeriodo(periodo);

        List<NfeRegistro> devolucoes = nfes.stream()
            .filter(n -> {
                String cfop = n.cfop() != null ? n.cfop().trim() : "";
                String tipo = n.tipoOperacao() != null ? n.tipoOperacao().toLowerCase() : "";
                return cfop.startsWith("1201") || cfop.startsWith("1202") ||
                       cfop.startsWith("1410") || cfop.startsWith("1411") ||
                       cfop.startsWith("2201") || cfop.startsWith("2202") ||
                       cfop.startsWith("5201") || cfop.startsWith("5202") ||
                       cfop.startsWith("5410") || cfop.startsWith("5411") ||
                       tipo.contains("devol") || tipo.contains("retorno");
            })
            .toList();

        BigDecimal totalDev = devolucoes.stream().map(NfeRegistro::valorFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIcms = devolucoes.stream().map(NfeRegistro::valorIcms).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIpi = devolucoes.stream().map(NfeRegistro::valorIpi).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Also check NFCe cancelled (returns)
        long cancelados = nfes.stream().filter(n -> "S".equals(n.cancelado())).count();

        Path pdfPath = outputDir.resolve("PDF").resolve("Devolucoes_" + periodo.mesAnoRef() + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                y = text(cs, "RELATORIO DE DEVOLUCOES", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = text(cs, "Periodo: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT), MARGIN, y);
                y -= LH * 2;

                // Summary
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                y = text(cs, "RESUMO DE DEVOLUCOES", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = text(cs, "Total de notas de devolucao: " + devolucoes.size(), MARGIN + 20, y);
                y = text(cs, "Total NFe canceladas no periodo: " + cancelados, MARGIN + 20, y);
                y -= LH;
                y = text(cs, "Valor total das devolucoes: R$ " + money(totalDev), MARGIN + 20, y);
                y = text(cs, "ICMS das devolucoes: R$ " + money(totalIcms), MARGIN + 20, y);
                y = text(cs, "IPI das devolucoes: R$ " + money(totalIpi), MARGIN + 20, y);
                y -= LH * 2;

                // Detail
                if (!devolucoes.isEmpty()) {
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                    y = text(cs, "DETALHAMENTO", MARGIN, y);
                    y -= 4;

                    cs.setFont(PDType1Font.HELVETICA_BOLD, 8);
                    y = text(cs, String.format("%-8s %-12s %-6s %-6s %12s %10s %10s %-10s",
                        "Numero", "Data", "CFOP", "Tipo", "Valor", "ICMS", "IPI", "Status"), MARGIN + 10, y);

                    cs.setFont(PDType1Font.HELVETICA, 8);
                    int count = 0;
                    for (NfeRegistro nfe : devolucoes) {
                        if (count++ > 40) {
                            y = text(cs, "... mais " + (devolucoes.size() - 40) + " notas", MARGIN + 10, y);
                            break;
                        }
                        y = text(cs, String.format("%-8d %-12s %-6s %-6s %12s %10s %10s %-10s",
                            nfe.nfeNumero(),
                            nfe.nfeDataEmissao() != null ? nfe.nfeDataEmissao().format(FMT) : "",
                            trunc(nfe.cfop(), 6),
                            trunc(nfe.entradaSaida(), 6),
                            money(nfe.valorFinal()),
                            money(nfe.valorIcms()),
                            money(nfe.valorIpi()),
                            trunc(nfe.nfeStatus(), 10)
                        ), MARGIN + 10, y);
                        if (y < MARGIN + 40) break;
                    }
                } else {
                    cs.setFont(PDType1Font.HELVETICA, 10);
                    y = text(cs, "Nenhuma devolucao encontrada no periodo.", MARGIN + 20, y);
                }
            }
            doc.save(pdfPath.toFile());
        }
        log.info("PDF Devolucoes gerado: {}", pdfPath);
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
