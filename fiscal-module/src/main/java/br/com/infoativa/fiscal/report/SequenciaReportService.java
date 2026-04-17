package br.com.infoativa.fiscal.report;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Connection;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

/**
 * Relatório de Sequência de Numeração.
 * Detecta buracos na numeração de NFe/NFCe e exibe totais por dia.
 */
public class SequenciaReportService {

    private static final Logger log = LoggerFactory.getLogger(SequenciaReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float M = 40f;
    private static final float LH = 14f;
    private static final float PW = PDRectangle.A4.getWidth();
    private static final float PH = PDRectangle.A4.getHeight();

    public void gerar(Connection conn, Periodo periodo, Path outputDir,
                       String nomeEmpresa, String cnpj) throws Exception {
        List<NfeRegistro>  nfes  = new NfeRepository(conn).findByPeriodo(periodo);
        List<NfceRegistro> nfces = new NfceRepository(conn).findByPeriodo(periodo);

        if (nfes.isEmpty() && nfces.isEmpty()) {
            log.info("Sem dados para relatório de sequência: {}", periodo.descricao());
            return;
        }

        Path pdf = outputDir.resolve("PDF").resolve("Sequencias_" + periodo.mesAnoRef() + ".pdf");
        Files.createDirectories(pdf.getParent());

        try (PDDocument doc = new PDDocument()) {
            PDFont fn = loadFont(doc, false);
            PDFont fb = loadFont(doc, true);

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PH - M;
                y = cabecalho(cs, fb, fn, nomeEmpresa, cnpj, y);
                y = titulo(cs, fb, "RELATÓRIO DE SEQUÊNCIA DE NUMERAÇÃO", periodo, y);
                y -= 10;

                // ── Resumo NFe ────────────────────────────────────────────
                y = secao(cs, fb, "NOTAS FISCAIS ELETRÔNICAS (NFe – Modelo 55)", y);

                // Detectar buracos na sequência
                List<Integer> numeros = nfes.stream()
                    .map(NfeRegistro::nfeNumero)
                    .filter(n -> n > 0)
                    .sorted()
                    .collect(Collectors.toList());

                List<String> buracos = detectarBuracos(numeros);

                y = resumoBox(cs, fn, fb, new String[][]{
                    {"Total emitidas",  String.valueOf(nfes.stream().filter(n -> !"S".equals(n.cancelado())).count())},
                    {"Canceladas",      String.valueOf(nfes.stream().filter(n -> "S".equals(n.cancelado())).count())},
                    {"Primeira nº",     numeros.isEmpty() ? "—" : String.valueOf(numeros.get(0))},
                    {"Última nº",       numeros.isEmpty() ? "—" : String.valueOf(numeros.get(numeros.size()-1))},
                    {"Buracos na seq.", buracos.isEmpty() ? "✓ Nenhum" : String.valueOf(buracos.size()) + " intervalo(s)"},
                }, y);
                y -= 8;

                if (!buracos.isEmpty()) {
                    y = secao(cs, fb, "⚠ BURACOS NA NUMERAÇÃO (NFe)", y);
                    cs.setFont(fn, 8);
                    cs.setNonStrokingColor(new Color(185, 28, 28));
                    for (String b : buracos.subList(0, Math.min(10, buracos.size()))) {
                        if (y < M + 20) break;
                        cs.beginText();
                        cs.newLineAtOffset(M + 10, y);
                        cs.showText("  Faltando: " + b);
                        cs.endText();
                        y -= LH;
                    }
                    cs.setNonStrokingColor(Color.BLACK);
                }
                y -= 10;

                // ── Tabela por dia ─────────────────────────────────────────
                y = secao(cs, fb, "NFe POR DIA", y);
                String[] hdr = {"Data", "Qtd Emitidas", "Qtd Canceladas", "Valor Total", "Nº Inicial", "Nº Final"};
                float[] wids = {65, 80, 90, 90, 65, 65};
                y = tabelaCabec(cs, fb, hdr, wids, y);

                Map<String, List<NfeRegistro>> porDia = nfes.stream()
                    .filter(n -> n.nfeDataEmissao() != null)
                    .collect(Collectors.groupingBy(
                        n -> n.nfeDataEmissao().format(FMT),
                        TreeMap::new, Collectors.toList()));

                int lc = 0;
                for (Map.Entry<String, List<NfeRegistro>> e : porDia.entrySet()) {
                    if (y < M + 20) break;
                    List<NfeRegistro> dia = e.getValue();
                    long ativas = dia.stream().filter(n -> !"S".equals(n.cancelado())).count();
                    long cancl  = dia.stream().filter(n -> "S".equals(n.cancelado())).count();
                    BigDecimal tot = dia.stream().filter(n -> !"S".equals(n.cancelado()))
                        .map(NfeRegistro::valorFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
                    int minN = dia.stream().mapToInt(NfeRegistro::nfeNumero).min().orElse(0);
                    int maxN = dia.stream().mapToInt(NfeRegistro::nfeNumero).max().orElse(0);

                    String[] row = {e.getKey(), s(ativas), s(cancl), "R$ " + fmt(tot), s(minN), s(maxN)};
                    y = tabelaLinha(cs, fn, row, wids, y, lc++ % 2 == 0);
                }

                // ── NFCe ─────────────────────────────────────────────────
                y -= 15;
                if (!nfces.isEmpty() && y > M + 80) {
                    y = secao(cs, fb, "NFC-e POR DIA (Modelo 65)", y);
                    String[] hdr2 = {"Data", "Qtd Emitidas", "Qtd Canceladas", "Valor Total", "Nº Inicial", "Nº Final"};
                    y = tabelaCabec(cs, fb, hdr2, wids, y);

                    Map<String, List<NfceRegistro>> porDia2 = nfces.stream()
                        .filter(n -> n.nfceDataEmissao() != null)
                        .collect(Collectors.groupingBy(
                            n -> n.nfceDataEmissao().format(FMT),
                            TreeMap::new, Collectors.toList()));

                    lc = 0;
                    for (Map.Entry<String, List<NfceRegistro>> e : porDia2.entrySet()) {
                        if (y < M + 20) break;
                        List<NfceRegistro> dia = e.getValue();
                        long ativas = dia.stream().filter(n -> !"S".equals(n.cupomCancelado())).count();
                        long cancl  = dia.stream().filter(n -> "S".equals(n.cupomCancelado())).count();
                        BigDecimal tot = dia.stream().filter(n -> !"S".equals(n.cupomCancelado()))
                            .map(NfceRegistro::valorFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
                        int minN = dia.stream().mapToInt(NfceRegistro::nfceNumero).min().orElse(0);
                        int maxN = dia.stream().mapToInt(NfceRegistro::nfceNumero).max().orElse(0);

                        String[] row = {e.getKey(), s(ativas), s(cancl), "R$ " + fmt(tot), s(minN), s(maxN)};
                        y = tabelaLinha(cs, fn, row, wids, y, lc++ % 2 == 0);
                    }
                }

                rodape(cs, fn, 1);
            }

            doc.save(pdf.toFile());
            log.info("PDF Sequências gerado: {}", pdf);
        }
    }

    private List<String> detectarBuracos(List<Integer> nums) {
        List<String> buracos = new ArrayList<>();
        for (int i = 1; i < nums.size(); i++) {
            int prev = nums.get(i - 1);
            int curr = nums.get(i);
            if (curr - prev > 1) {
                buracos.add((prev + 1) + " a " + (curr - 1));
            }
        }
        return buracos;
    }

    // ── Helpers de desenho ────────────────────────────────────────────────────

    private float cabecalho(PDPageContentStream cs, PDFont fb, PDFont fn,
                              String nome, String cnpj, float y) throws IOException {
        fill(cs, M - 5, y - 8, PW - 2 * M + 10, 45, new Color(26, 86, 219));
        txt(cs, fb, 13, Color.WHITE, M + 5, y + 10, nome != null ? nome : "EMPRESA");
        txt(cs, fn, 9, new Color(200, 210, 230), M + 5, y - 3, "CNPJ: " + fmtCnpj(cnpj));
        cs.setNonStrokingColor(Color.BLACK);
        return y - 50;
    }

    private float titulo(PDPageContentStream cs, PDFont fb, String t, Periodo p, float y) throws IOException {
        txt(cs, fb, 14, new Color(17, 24, 39), M, y, t);
        y -= LH + 2;
        txt(cs, fb, 9, new Color(107, 114, 128), M, y,
            "Período: " + p.inicio().format(FMT) + " a " + p.fim().format(FMT));
        y -= 8;
        cs.setStrokingColor(new Color(26, 86, 219));
        cs.setLineWidth(2f); cs.moveTo(M, y); cs.lineTo(PW - M, y); cs.stroke();
        cs.setLineWidth(1f); cs.setStrokingColor(Color.BLACK);
        return y - 8;
    }

    private float secao(PDPageContentStream cs, PDFont fb, String t, float y) throws IOException {
        y -= 5;
        fill(cs, M, y - 4, PW - 2 * M, 18, new Color(239, 246, 255));
        txt(cs, fb, 10, new Color(26, 86, 219), M + 6, y + 4, t);
        cs.setNonStrokingColor(Color.BLACK);
        return y - 18;
    }

    private float resumoBox(PDPageContentStream cs, PDFont fn, PDFont fb,
                             String[][] rows, float y) throws IOException {
        y -= 5;
        float h = rows.length * LH + 10;
        borda(cs, M, y - h, PW - 2 * M, h, new Color(209, 213, 219));
        float ly = y - 10;
        for (int i = 0; i < rows.length; i++) {
            PDFont f = (i == rows.length - 1) ? fb : fn;
            txt(cs, f, 9, new Color(17, 24, 39), M + 12, ly, rows[i][0]);
            txt(cs, f, 9, new Color(17, 24, 39), PW - M - 100, ly, rows[i][1]);
            ly -= LH;
        }
        return y - h - 5;
    }

    private float tabelaCabec(PDPageContentStream cs, PDFont fb, String[] hdrs, float[] ws, float y) throws IOException {
        fill(cs, M, y - LH, sum(ws), LH + 4, new Color(26, 86, 219));
        cs.beginText(); cs.setFont(fb, 8); cs.setNonStrokingColor(Color.WHITE);
        float cx = M + 4;
        for (int i = 0; i < hdrs.length; i++) {
            cs.newLineAtOffset(i == 0 ? cx : ws[i - 1], 0);
            cs.showText(trunc(hdrs[i], (int)(ws[i] / 5)));
            cx += ws[i];
        }
        cs.endText(); cs.setNonStrokingColor(Color.BLACK);
        return y - LH - 4;
    }

    private float tabelaLinha(PDPageContentStream cs, PDFont fn, String[] cells,
                               float[] ws, float y, boolean par) throws IOException {
        if (par) fill(cs, M, y - LH + 2, sum(ws), LH, new Color(243, 244, 246));
        cs.beginText(); cs.setFont(fn, 8); cs.setNonStrokingColor(new Color(17, 24, 39));
        float cx = M + 4;
        for (int i = 0; i < cells.length; i++) {
            cs.newLineAtOffset(i == 0 ? cx : ws[i - 1], 0);
            cs.showText(trunc(cells[i] != null ? cells[i] : "", (int)(ws[i] / 5)));
            cx += ws[i];
        }
        cs.endText(); cs.setNonStrokingColor(Color.BLACK);
        return y - LH;
    }

    private void rodape(PDPageContentStream cs, PDFont fn, int pg) throws IOException {
        cs.setStrokingColor(new Color(209, 213, 219));
        cs.moveTo(M, 30); cs.lineTo(PW - M, 30); cs.stroke();
        txt(cs, fn, 7, new Color(156, 163, 175), M, 18,
            "Módulo Fiscal InfoAtiva  |  Página " + pg);
        cs.setNonStrokingColor(Color.BLACK);
    }

    private void txt(PDPageContentStream cs, PDFont f, int sz, Color cor,
                      float x, float y, String t) throws IOException {
        cs.beginText(); cs.setFont(f, sz); cs.setNonStrokingColor(cor);
        cs.newLineAtOffset(x, y); cs.showText(t != null ? t : ""); cs.endText();
    }

    private void fill(PDPageContentStream cs, float x, float y, float w, float h, Color c) throws IOException {
        cs.setNonStrokingColor(c); cs.addRect(x, y, w, h); cs.fill(); cs.setNonStrokingColor(Color.BLACK);
    }

    private void borda(PDPageContentStream cs, float x, float y, float w, float h, Color c) throws IOException {
        cs.setStrokingColor(c); cs.addRect(x, y, w, h); cs.stroke(); cs.setStrokingColor(Color.BLACK);
    }

    private float sum(float[] ws) { float s = 0; for (float v : ws) s += v; return s; }

    private String trunc(String s, int m) { return s.length() > m ? s.substring(0, m) : s; }

    private String fmt(BigDecimal v) {
        if (v == null) return "0,00";
        return String.format("%,.2f", v).replace(",", "X").replace(".", ",").replace("X", ".");
    }

    private String s(Object o) { return o == null ? "" : o.toString(); }

    private String fmtCnpj(String c) {
        if (c == null || c.length() != 14) return c != null ? c : "";
        return c.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    private PDFont loadFont(PDDocument doc, boolean bold) {
        String res = bold ? "/fonts/NotoSans-Bold.ttf" : "/fonts/NotoSans-Regular.ttf";
        try (InputStream is = getClass().getResourceAsStream(res)) {
            if (is != null) return PDType0Font.load(doc, is, true);
        } catch (Exception ignored) {}
        String arialPath = bold ? "C:/Windows/Fonts/arialbd.ttf" : "C:/Windows/Fonts/arial.ttf";
        try { return PDType0Font.load(doc, new File(arialPath), true); } catch (Exception ignored) {}
        return bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
    }
}
