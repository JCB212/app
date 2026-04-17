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
import java.util.stream.Collectors;

/**
 * Relatório de CST / CFOP — análoga ao registro C190 do SPED Fiscal.
 * Agrupa vendas por combinação CST+CFOP com totais de valor, ICMS, IPI.
 */
public class CstCfopReportService {

    private static final Logger log = LoggerFactory.getLogger(CstCfopReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float M = 40f, LH = 14f;
    private static final float PW = PDRectangle.A4.getWidth();
    private static final float PH = PDRectangle.A4.getHeight();

    public void gerar(Connection conn, Periodo periodo, Path outputDir,
                       String nomeEmpresa, String cnpj) throws Exception {
        List<NfeRegistro>  nfes  = new NfeRepository(conn).findByPeriodo(periodo);
        List<NfceRegistro> nfces = new NfceRepository(conn).findByPeriodo(periodo);

        if (nfes.isEmpty() && nfces.isEmpty()) {
            log.info("Sem dados para relatório CST/CFOP: {}", periodo.descricao());
            return;
        }

        Path pdf = outputDir.resolve("PDF").resolve("CST_CFOP_" + periodo.mesAnoRef() + ".pdf");
        Files.createDirectories(pdf.getParent());

        // Agrupar por CFOP
        Map<String, BigDecimal[]> agrupNfe = new TreeMap<>();
        for (NfeRegistro n : nfes) {
            if ("S".equals(n.cancelado())) continue;
            String cfop = n.cfop() != null ? n.cfop() : "5102";
            agrupNfe.computeIfAbsent(cfop, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] v = agrupNfe.get(cfop);
            v[0] = v[0].add(n.totalProdutos() != null ? n.totalProdutos() : BigDecimal.ZERO);
            v[1] = v[1].add(n.valorBaseIcms() != null ? n.valorBaseIcms() : BigDecimal.ZERO);
            v[2] = v[2].add(n.valorIcms() != null ? n.valorIcms() : BigDecimal.ZERO);
            v[3] = v[3].add(n.valorIpi() != null ? n.valorIpi() : BigDecimal.ZERO);
        }

        Map<String, BigDecimal[]> agrupNfce = new TreeMap<>();
        for (NfceRegistro n : nfces) {
            if ("S".equals(n.cupomCancelado())) continue;
            String cfop = n.cfop() != null ? n.cfop() : "5405";
            agrupNfce.computeIfAbsent(cfop, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] v = agrupNfce.get(cfop);
            v[0] = v[0].add(n.totalProdutos() != null ? n.totalProdutos() : BigDecimal.ZERO);
            v[1] = v[1].add(n.baseIcms() != null ? n.baseIcms() : BigDecimal.ZERO);
            v[2] = v[2].add(n.icms() != null ? n.icms() : BigDecimal.ZERO);
            v[3] = BigDecimal.ZERO;
        }

        try (PDDocument doc = new PDDocument()) {
            PDFont fn = loadFont(doc, false);
            PDFont fb = loadFont(doc, true);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PH - M;
                y = cab(cs, fb, fn, nomeEmpresa, cnpj, y);
                y = titulo(cs, fb, "RELATÓRIO CST / CFOP", periodo, y);
                y -= 8;

                // Totais gerais
                BigDecimal tVl = agrupNfe.values().stream().map(v -> v[0]).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(agrupNfce.values().stream().map(v -> v[0]).reduce(BigDecimal.ZERO, BigDecimal::add));
                BigDecimal tIcms = agrupNfe.values().stream().map(v -> v[2]).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(agrupNfce.values().stream().map(v -> v[2]).reduce(BigDecimal.ZERO, BigDecimal::add));

                y = boxResumo(cs, fn, fb, new String[][]{
                    {"Total de CFOPs distintos", s(agrupNfe.size() + agrupNfce.size())},
                    {"Valor total das operações", "R$ " + fmt(tVl)},
                    {"ICMS total apurado",         "R$ " + fmt(tIcms)},
                }, y);
                y -= 12;

                // Tabela NFe por CFOP
                if (!agrupNfe.isEmpty()) {
                    y = sec(cs, fb, "AGRUPAMENTO NFe (Modelo 55) POR CFOP — equivalente C190 SPED", y);
                    String[] hdr = {"CFOP", "Descrição CFOP", "Valor Operação", "Base ICMS", "Valor ICMS", "Valor IPI"};
                    float[] ws  = {45, 140, 80, 75, 75, 70};
                    y = cabecTbl(cs, fb, hdr, ws, y);
                    int lc = 0;
                    BigDecimal[] tots = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                    for (Map.Entry<String, BigDecimal[]> e : agrupNfe.entrySet()) {
                        if (y < M + 20) break;
                        BigDecimal[] v = e.getValue();
                        for (int i = 0; i < 4; i++) tots[i] = tots[i].add(v[i]);
                        y = linhaTabela(cs, fn, new String[]{
                            e.getKey(),
                            descCfop(e.getKey()),
                            "R$ " + fmt(v[0]),
                            "R$ " + fmt(v[1]),
                            "R$ " + fmt(v[2]),
                            "R$ " + fmt(v[3])
                        }, ws, y, lc++ % 2 == 0);
                    }
                    // Linha de total
                    y = linhaTabela(cs, fb, new String[]{
                        "TOTAL", "", "R$ " + fmt(tots[0]), "R$ " + fmt(tots[1]),
                        "R$ " + fmt(tots[2]), "R$ " + fmt(tots[3])
                    }, ws, y, false);
                }

                y -= 12;

                // Tabela NFCe por CFOP
                if (!agrupNfce.isEmpty() && y > M + 80) {
                    y = sec(cs, fb, "AGRUPAMENTO NFCe (Modelo 65) POR CFOP", y);
                    String[] hdr2 = {"CFOP", "Descrição CFOP", "Valor Operação", "Base ICMS", "Valor ICMS", "Valor IPI"};
                    float[] ws2   = {45, 140, 80, 75, 75, 70};
                    y = cabecTbl(cs, fb, hdr2, ws2, y);
                    int lc = 0;
                    BigDecimal[] tots = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                    for (Map.Entry<String, BigDecimal[]> e : agrupNfce.entrySet()) {
                        if (y < M + 20) break;
                        BigDecimal[] v = e.getValue();
                        for (int i = 0; i < 4; i++) tots[i] = tots[i].add(v[i]);
                        y = linhaTabela(cs, fn, new String[]{
                            e.getKey(), descCfop(e.getKey()),
                            "R$ " + fmt(v[0]), "R$ " + fmt(v[1]),
                            "R$ " + fmt(v[2]), "—"
                        }, ws2, y, lc++ % 2 == 0);
                    }
                    linhaTabela(cs, fb, new String[]{
                        "TOTAL", "", "R$ " + fmt(tots[0]), "R$ " + fmt(tots[1]),
                        "R$ " + fmt(tots[2]), "—"
                    }, ws2, y, false);
                }

                rodape(cs, fn, 1);
            }

            doc.save(pdf.toFile());
            log.info("PDF CST/CFOP gerado: {}", pdf);
        }
    }

    // Descrições padrão dos CFOPs mais comuns no cliente
    private String descCfop(String cfop) {
        return switch (cfop) {
            case "5102" -> "Venda de mercadoria adquirida de terceiros";
            case "5405" -> "Venda de mercadoria sujeita a ST (substituição tributária)";
            case "5110" -> "Venda de produção do estabelecimento";
            case "5201" -> "Devolução de compra para industrialização";
            case "5202" -> "Devolução de compra para comercialização";
            case "5910" -> "Remessa em bonificação";
            case "1102" -> "Compra de mercadoria para comercialização";
            case "1201" -> "Devolução de venda de prod. do estabelecimento";
            case "2102" -> "Compra de merc. de terceiros para comercialização";
            default     -> "Outras operações";
        };
    }

    // ── Helpers PDF ────────────────────────────────────────────────────────────

    private float cab(PDPageContentStream cs, PDFont fb, PDFont fn, String nome, String cnpj, float y) throws IOException {
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

    private float sec(PDPageContentStream cs, PDFont fb, String t, float y) throws IOException {
        y -= 5;
        fill(cs, M, y - 4, PW - 2 * M, 18, new Color(239, 246, 255));
        txt(cs, fb, 9, new Color(26, 86, 219), M + 6, y + 4, t);
        cs.setNonStrokingColor(Color.BLACK);
        return y - 18;
    }

    private float boxResumo(PDPageContentStream cs, PDFont fn, PDFont fb, String[][] rows, float y) throws IOException {
        y -= 5;
        float h = rows.length * LH + 10;
        borda(cs, M, y - h, PW - 2 * M, h, new Color(209, 213, 219));
        float ly = y - 10;
        for (String[] r : rows) {
            txt(cs, fn, 9, new Color(17, 24, 39), M + 12, ly, r[0]);
            txt(cs, fb, 9, new Color(17, 24, 39), PW - M - 110, ly, r[1]);
            ly -= LH;
        }
        return y - h - 5;
    }

    private float cabecTbl(PDPageContentStream cs, PDFont fb, String[] hdrs, float[] ws, float y) throws IOException {
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

    private float linhaTabela(PDPageContentStream cs, PDFont fn, String[] cells,
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

    private void rodape(PDPageContentStream cs, PDFont fn, int p) throws IOException {
        cs.setStrokingColor(new Color(209, 213, 219));
        cs.moveTo(M, 30); cs.lineTo(PW - M, 30); cs.stroke();
        txt(cs, fn, 7, new Color(156, 163, 175), M, 18,
            "Módulo Fiscal InfoAtiva  |  Página " + p);
    }

    private void txt(PDPageContentStream cs, PDFont f, int sz, Color c, float x, float y, String t) throws IOException {
        cs.beginText(); cs.setFont(f, sz); cs.setNonStrokingColor(c);
        cs.newLineAtOffset(x, y); cs.showText(t != null ? t : ""); cs.endText();
    }

    private void fill(PDPageContentStream cs, float x, float y, float w, float h, Color c) throws IOException {
        cs.setNonStrokingColor(c); cs.addRect(x, y, w, h); cs.fill(); cs.setNonStrokingColor(Color.BLACK);
    }

    private void borda(PDPageContentStream cs, float x, float y, float w, float h, Color c) throws IOException {
        cs.setStrokingColor(c); cs.addRect(x, y, w, h); cs.stroke(); cs.setStrokingColor(Color.BLACK);
    }

    private String trunc(String s, int m) { return s.length() > m ? s.substring(0, m) : s; }
    private float sum(float[] ws) { float s = 0; for (float v : ws) s += v; return s; }
    private String fmt(BigDecimal v) {
        if (v == null) return "0,00";
        return String.format("%,.2f", v).replace(",", "X").replace(".", ",").replace("X", ".");
    }
    private String fmtCnpj(String c) {
        if (c == null || c.length() != 14) return c != null ? c : "";
        return c.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }
    private String s(Object o) { return o == null ? "" : o.toString(); }

    private PDFont loadFont(PDDocument doc, boolean b) {
        String res = b ? "/fonts/NotoSans-Bold.ttf" : "/fonts/NotoSans-Regular.ttf";
        try (InputStream is = getClass().getResourceAsStream(res)) {
            if (is != null) return PDType0Font.load(doc, is, true);
        } catch (Exception ignored) {}
        try { return PDType0Font.load(doc, new File(b ? "C:/Windows/Fonts/arialbd.ttf" : "C:/Windows/Fonts/arial.ttf"), true); }
        catch (Exception ignored) {}
        return b ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
    }
}
