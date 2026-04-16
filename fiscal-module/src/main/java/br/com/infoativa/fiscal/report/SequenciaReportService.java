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

public class SequenciaReportService {

    private static final Logger log = LoggerFactory.getLogger(SequenciaReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 40;
    private static final float LH = 13;

    public void gerar(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);

        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);

        // Find gaps in NFCe numbering
        List<int[]> gapsNfce = findGaps(nfces.stream().map(NfceRegistro::nfceNumero).sorted().toList());
        List<int[]> gapsNfe = findGaps(nfes.stream().map(NfeRegistro::nfeNumero).sorted().toList());

        // Cancelled
        long cancNfce = nfces.stream().filter(n -> "S".equals(n.cupomCancelado())).count();
        long cancNfe = nfes.stream().filter(n -> "S".equals(n.cancelado())).count();

        Path pdfPath = outputDir.resolve("PDF").resolve("Sequencias_" + periodo.mesAnoRef() + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                y = text(cs, "RELATORIO DE SEQUENCIAS", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                y = text(cs, "Periodo: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT), MARGIN, y);
                y -= LH * 2;

                // NFCe summary
                cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
                y = text(cs, "NFCe - Cupom Fiscal Eletronico", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                if (!nfces.isEmpty()) {
                    int first = nfces.stream().mapToInt(NfceRegistro::nfceNumero).min().orElse(0);
                    int last = nfces.stream().mapToInt(NfceRegistro::nfceNumero).max().orElse(0);
                    y = text(cs, "Total de documentos: " + nfces.size(), MARGIN + 20, y);
                    y = text(cs, "Numeracao: " + first + " a " + last, MARGIN + 20, y);
                    y = text(cs, "Cancelados: " + cancNfce, MARGIN + 20, y);
                    y = text(cs, "Furos de sequencia: " + gapsNfce.size(), MARGIN + 20, y);
                    if (!gapsNfce.isEmpty()) {
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                        y = text(cs, "Furos encontrados:", MARGIN + 20, y - 4);
                        cs.setFont(PDType1Font.HELVETICA, 9);
                        int shown = 0;
                        for (int[] gap : gapsNfce) {
                            if (shown++ > 20) { y = text(cs, "... mais " + (gapsNfce.size() - 20) + " furos", MARGIN + 30, y); break; }
                            if (gap[0] == gap[1]) {
                                y = text(cs, "Numero: " + gap[0], MARGIN + 30, y);
                            } else {
                                y = text(cs, "Numeros: " + gap[0] + " a " + gap[1] + " (" + (gap[1] - gap[0] + 1) + " documentos)", MARGIN + 30, y);
                            }
                        }
                    } else {
                        cs.setFont(PDType1Font.HELVETICA, 10);
                        y = text(cs, "Sequencia OK - Sem furos encontrados", MARGIN + 20, y);
                    }
                } else {
                    y = text(cs, "Nenhuma NFCe no periodo", MARGIN + 20, y);
                }

                y -= LH * 2;

                // NFe summary
                cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
                y = text(cs, "NFe - Nota Fiscal Eletronica", MARGIN, y);
                cs.setFont(PDType1Font.HELVETICA, 10);
                if (!nfes.isEmpty()) {
                    int first = nfes.stream().mapToInt(NfeRegistro::nfeNumero).min().orElse(0);
                    int last = nfes.stream().mapToInt(NfeRegistro::nfeNumero).max().orElse(0);
                    y = text(cs, "Total de documentos: " + nfes.size(), MARGIN + 20, y);
                    y = text(cs, "Numeracao: " + first + " a " + last, MARGIN + 20, y);
                    y = text(cs, "Cancelados: " + cancNfe, MARGIN + 20, y);
                    y = text(cs, "Furos de sequencia: " + gapsNfe.size(), MARGIN + 20, y);
                    if (!gapsNfe.isEmpty()) {
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                        y = text(cs, "Furos encontrados:", MARGIN + 20, y - 4);
                        cs.setFont(PDType1Font.HELVETICA, 9);
                        int shown = 0;
                        for (int[] gap : gapsNfe) {
                            if (shown++ > 20) { y = text(cs, "... mais " + (gapsNfe.size() - 20), MARGIN + 30, y); break; }
                            if (gap[0] == gap[1]) {
                                y = text(cs, "Numero: " + gap[0], MARGIN + 30, y);
                            } else {
                                y = text(cs, "Numeros: " + gap[0] + " a " + gap[1] + " (" + (gap[1] - gap[0] + 1) + " documentos)", MARGIN + 30, y);
                            }
                        }
                    } else {
                        cs.setFont(PDType1Font.HELVETICA, 10);
                        y = text(cs, "Sequencia OK - Sem furos encontrados", MARGIN + 20, y);
                    }
                } else {
                    y = text(cs, "Nenhuma NFe no periodo", MARGIN + 20, y);
                }
            }
            doc.save(pdfPath.toFile());
        }
        log.info("PDF Sequencias gerado: {}", pdfPath);
    }

    private List<int[]> findGaps(List<Integer> numbers) {
        List<int[]> gaps = new ArrayList<>();
        if (numbers.size() < 2) return gaps;
        for (int i = 1; i < numbers.size(); i++) {
            int prev = numbers.get(i - 1);
            int curr = numbers.get(i);
            if (curr - prev > 1) {
                gaps.add(new int[]{prev + 1, curr - 1});
            }
        }
        return gaps;
    }

    private float text(PDPageContentStream cs, String t, float x, float y) throws IOException {
        cs.beginText(); cs.newLineAtOffset(x, y); cs.showText(t); cs.endText();
        return y - LH;
    }
}
