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
 * Relatório de Devoluções de Vendas.
 * Detecta notas com CFOP de devolução (1201, 1202, 2201, 2202, 5201, 5202)
 * e notas canceladas com valor representativo.
 * Essencial para o contador verificar créditos de ICMS/PIS/COFINS.
 */
public class DevolucoesReportService {

    private static final Logger log = LoggerFactory.getLogger(DevolucoesReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float M = 40f, LH = 14f;
    private static final float PW = PDRectangle.A4.getWidth();
    private static final float PH = PDRectangle.A4.getHeight();

    // CFOPs de devolução / retorno
    private static final Set<String> CFOP_DEVOLUCAO = Set.of(
        "1201", "1202", "1203", "1204", "1205",  // devoluções de compras (entrada)
        "2201", "2202", "2203", "2204", "2205",  // devoluções interestas
        "5201", "5202", "5203", "5204", "5205",  // devoluções de vendas (saída retorno)
        "6201", "6202", "6203"                   // devoluções interestaduais
    );

    public void gerar(Connection conn, Periodo periodo, Path outputDir,
                       String nomeEmpresa, String cnpj) throws Exception {
        List<NfeRegistro> nfes = new NfeRepository(conn).findByPeriodo(periodo);

        // Separar por tipo
        List<NfeRegistro> devolvidas = nfes.stream()
            .filter(n -> n.cfop() != null && CFOP_DEVOLUCAO.contains(n.cfop()))
            .collect(Collectors.toList());

        List<NfeRegistro> canceladas = nfes.stream()
            .filter(n -> "S".equals(n.cancelado()))
            .collect(Collectors.toList());

        if (devolvidas.isEmpty() && canceladas.isEmpty()) {
            log.info("Sem devoluções no período: {}", periodo.descricao());
        }

        Path pdf = outputDir.resolve("PDF").resolve("Devolucoes_" + periodo.mesAnoRef() + ".pdf");
        Files.createDirectories(pdf.getParent());

        try (PDDocument doc = new PDDocument()) {
            PDFont fn = loadFont(doc, false);
            PDFont fb = loadFont(doc, true);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PH - M;
                y = cab(cs, fb, fn, nomeEmpresa, cnpj, y);
                y = titulo(cs, fb, "RELATÓRIO DE DEVOLUÇÕES E CANCELAMENTOS", periodo, y);
                y -= 8;

                // Totais
                BigDecimal totalDev  = devolvidas.stream().map(NfeRegistro::valorFinal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalCanc = canceladas.stream().map(NfeRegistro::valorFinal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal icmsDev   = devolvidas.stream().map(NfeRegistro::valorIcms)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal pisDev    = devolvidas.stream().map(NfeRegistro::valorPis)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal cofinsDev = devolvidas.stream().map(NfeRegistro::valorCofins)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                y = boxResumo(cs, fn, fb, new String[][]{
                    {"Notas de devolução",        String.valueOf(devolvidas.size())},
                    {"Valor total devolvido",      "R$ " + fmt(totalDev)},
                    {"ICMS a recuperar (devol.)", "R$ " + fmt(icmsDev)},
                    {"PIS a recuperar (devol.)",  "R$ " + fmt(pisDev)},
                    {"COFINS a recuperar (devol.)","R$ " + fmt(cofinsDev)},
                    {"Notas canceladas",           String.valueOf(canceladas.size())},
                    {"Valor notas canceladas",     "R$ " + fmt(totalCanc)},
                }, y);
                y -= 10;

                // Aviso de crédito
                if (!devolvidas.isEmpty()) {
                    fill(cs, M, y - 26, PW - 2 * M, 28, new Color(240, 253, 244));
                    borda(cs, M, y - 26, PW - 2 * M, 28, new Color(134, 239, 172));
                    txt(cs, fn, 8, new Color(22, 101, 52), M + 8, y - 6,
                        "✓ Crédito de ICMS nas devoluções de compras (CFOP 1201/2201): lançar em E110 do SPED Fiscal.");
                    txt(cs, fn, 8, new Color(22, 101, 52), M + 8, y - 18,
                        "✓ Crédito de PIS/COFINS nas devoluções de compras: lançar em M200/M600 do SPED Contribuições.");
                    y -= 34;
                }

                // Tabela de devoluções
                if (!devolvidas.isEmpty()) {
                    y = sec(cs, fb, "NOTAS DE DEVOLUÇÃO", y);
                    String[] hdr = {"Nº NFe", "Data", "Cliente/Fornec.", "CFOP", "Tipo", "Valor", "ICMS", "PIS", "COFINS"};
                    float[] ws  = {52, 58, 100, 38, 45, 60, 52, 45, 55};
                    y = cabecTbl(cs, fb, hdr, ws, y);
                    int lc = 0;
                    for (NfeRegistro n : devolvidas) {
                        if (y < M + 20) break;
                        String tipo = isCfopEntrada(n.cfop()) ? "ENTRADA" : "SAÍDA";
                        String dt   = n.nfeDataEmissao() != null ? n.nfeDataEmissao().format(FMT) : "";
                        y = linhaTabela(cs, fn, new String[]{
                            s(n.nfeNumero()), dt,
                            trunc(n.nomeCliente() != null ? n.nomeCliente() : "", 16),
                            n.cfop(),  tipo,
                            "R$ " + fmt(n.valorFinal()),
                            "R$ " + fmt(n.valorIcms()),
                            "R$ " + fmt(n.valorPis()),
                            "R$ " + fmt(n.valorCofins())
                        }, ws, y, lc++ % 2 == 0);
                    }
                }

                y -= 12;

                // Tabela de cancelamentos
                if (!canceladas.isEmpty() && y > M + 80) {
                    y = sec(cs, fb, "NOTAS CANCELADAS", y);
                    String[] hdr2 = {"Nº NFe", "Data", "Cliente", "Chave de Acesso", "Valor Cancelado"};
                    float[] ws2   = {55, 65, 110, 160, 80};
                    y = cabecTbl(cs, fb, hdr2, ws2, y);
                    int lc = 0;
                    for (NfeRegistro n : canceladas) {
                        if (y < M + 20) break;
                        String dt = n.nfeDataEmissao() != null ? n.nfeDataEmissao().format(FMT) : "";
                        y = linhaTabela(cs, fn, new String[]{
                            s(n.nfeNumero()), dt,
                            trunc(n.nomeCliente() != null ? n.nomeCliente() : "", 18),
                            trunc(n.nfeChaveAcesso() != null ? n.nfeChaveAcesso() : "", 44),
                            "R$ " + fmt(n.valorFinal())
                        }, ws2, y, lc++ % 2 == 0);
                    }
                }

                if (devolvidas.isEmpty() && canceladas.isEmpty()) {
                    txt(cs, fn, 11, new Color(107, 114, 128), M, y - 30,
                        "✓ Nenhuma devolução ou cancelamento encontrado no período.");
                }

                rodape(cs, fn, 1);
            }

            doc.save(pdf.toFile());
            log.info("PDF Devoluções gerado: {}", pdf);
        }
    }

    private boolean isCfopEntrada(String cfop) {
        return cfop != null && (cfop.startsWith("1") || cfop.startsWith("2"));
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
        txt(cs, fb, 13, new Color(17, 24, 39), M, y, t);
        y -= LH + 2;
        txt(cs, fb, 9, new Color(107, 114, 128), M, y, "Período: " + p.inicio().format(FMT) + " a " + p.fim().format(FMT));
        y -= 8;
        cs.setStrokingColor(new Color(26, 86, 219));
        cs.setLineWidth(2f); cs.moveTo(M, y); cs.lineTo(PW - M, y); cs.stroke();
        cs.setLineWidth(1f); cs.setStrokingColor(Color.BLACK);
        return y - 8;
    }
    private float sec(PDPageContentStream cs, PDFont fb, String t, float y) throws IOException {
        y -= 5; fill(cs, M, y - 4, PW - 2 * M, 18, new Color(239, 246, 255));
        txt(cs, fb, 9, new Color(26, 86, 219), M + 6, y + 4, t);
        cs.setNonStrokingColor(Color.BLACK); return y - 18;
    }
    private float boxResumo(PDPageContentStream cs, PDFont fn, PDFont fb, String[][] rows, float y) throws IOException {
        y -= 5; float h = rows.length * LH + 10;
        borda(cs, M, y - h, PW - 2 * M, h, new Color(209, 213, 219));
        float ly = y - 10;
        for (String[] r : rows) { txt(cs, fn, 9, new Color(17, 24, 39), M + 12, ly, r[0]); txt(cs, fb, 9, new Color(17, 24, 39), PW - M - 110, ly, r[1]); ly -= LH; }
        return y - h - 5;
    }
    private float cabecTbl(PDPageContentStream cs, PDFont fb, String[] hdrs, float[] ws, float y) throws IOException {
        fill(cs, M, y - LH, sum(ws), LH + 4, new Color(26, 86, 219));
        cs.beginText(); cs.setFont(fb, 7); cs.setNonStrokingColor(Color.WHITE);
        float cx = M + 4;
        for (int i = 0; i < hdrs.length; i++) { cs.newLineAtOffset(i == 0 ? cx : ws[i - 1], 0); cs.showText(trunc(hdrs[i], (int)(ws[i] / 5))); cx += ws[i]; }
        cs.endText(); cs.setNonStrokingColor(Color.BLACK); return y - LH - 4;
    }
    private float linhaTabela(PDPageContentStream cs, PDFont fn, String[] cells, float[] ws, float y, boolean par) throws IOException {
        if (par) fill(cs, M, y - LH + 2, sum(ws), LH, new Color(243, 244, 246));
        cs.beginText(); cs.setFont(fn, 7); cs.setNonStrokingColor(new Color(17, 24, 39));
        float cx = M + 4;
        for (int i = 0; i < cells.length; i++) { cs.newLineAtOffset(i == 0 ? cx : ws[i - 1], 0); cs.showText(trunc(cells[i] != null ? cells[i] : "", (int)(ws[i] / 5))); cx += ws[i]; }
        cs.endText(); cs.setNonStrokingColor(Color.BLACK); return y - LH;
    }
    private void rodape(PDPageContentStream cs, PDFont fn, int p) throws IOException {
        cs.setStrokingColor(new Color(209, 213, 219)); cs.moveTo(M, 30); cs.lineTo(PW - M, 30); cs.stroke();
        txt(cs, fn, 7, new Color(156, 163, 175), M, 18, "Módulo Fiscal InfoAtiva  |  Página " + p);
    }
    private void txt(PDPageContentStream cs, PDFont f, int sz, Color c, float x, float y, String t) throws IOException {
        cs.beginText(); cs.setFont(f, sz); cs.setNonStrokingColor(c); cs.newLineAtOffset(x, y); cs.showText(t != null ? t : ""); cs.endText();
    }
    private void fill(PDPageContentStream cs, float x, float y, float w, float h, Color c) throws IOException {
        cs.setNonStrokingColor(c); cs.addRect(x, y, w, h); cs.fill(); cs.setNonStrokingColor(Color.BLACK);
    }
    private void borda(PDPageContentStream cs, float x, float y, float w, float h, Color c) throws IOException {
        cs.setStrokingColor(c); cs.addRect(x, y, w, h); cs.stroke(); cs.setStrokingColor(Color.BLACK);
    }
    private String trunc(String s, int m) { return s != null && s.length() > m ? s.substring(0, m) : (s != null ? s : ""); }
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
        try (InputStream is = getClass().getResourceAsStream(res)) { if (is != null) return PDType0Font.load(doc, is, true); } catch (Exception ignored) {}
        try { return PDType0Font.load(doc, new File(b ? "C:/Windows/Fonts/arialbd.ttf" : "C:/Windows/Fonts/arial.ttf"), true); } catch (Exception ignored) {}
        return b ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
    }
}
