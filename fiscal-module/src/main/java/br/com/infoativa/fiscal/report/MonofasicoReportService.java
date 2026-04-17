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
import java.math.RoundingMode;
import java.nio.file.*;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Relatório de Tributação Monofásica PIS/COFINS.
 * Identifica produtos com NCM sujeitos à substituição tributária de PIS/COFINS
 * (produtos monofásicos conforme Lei 10.147/2000 e alterações).
 *
 * Análogo ao registro M410/M810 do SPED Contribuições.
 */
public class MonofasicoReportService {

    private static final Logger log = LoggerFactory.getLogger(MonofasicoReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float M = 40f, LH = 14f;
    private static final float PW = PDRectangle.A4.getWidth();
    private static final float PH = PDRectangle.A4.getHeight();

    // NCMs sujeitos à monofasia (conforme legislação federal vigente)
    private static final Set<String> NCM_MONOFASICO = Set.of(
        "3003", "3004", // Medicamentos (PIS 0% / COFINS 0% ou ST)
        "2106", "2201", "2202", // Bebidas
        "2710",                  // Combustíveis (gasolina, diesel)
        "2711",                  // GLP
        "8517", "8528",          // Eletroeletrônicos
        "4011", "4013",          // Pneus e câmaras
        "7309", "7310", "7311",  // Recipientes de gás
        "3808",                  // Pesticidas
        "2523"                   // Cimento
    );

    public void gerar(Connection conn, Periodo periodo, Path outputDir,
                       String nomeEmpresa, String cnpj) throws Exception {

        // Buscar itens de compra com NCM potencialmente monofásico
        List<ItemMonofasico> itens = buscarItensMonofasicos(conn, periodo);

        if (itens.isEmpty()) {
            log.info("Sem produtos monofásicos no período: {}", periodo.descricao());
            // Gerar PDF informativo mesmo sem dados
        }

        Path pdf = outputDir.resolve("PDF").resolve("Monofasico_" + periodo.mesAnoRef() + ".pdf");
        Files.createDirectories(pdf.getParent());

        try (PDDocument doc = new PDDocument()) {
            PDFont fn = loadFont(doc, false);
            PDFont fb = loadFont(doc, true);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PH - M;
                y = cab(cs, fb, fn, nomeEmpresa, cnpj, y);
                y = titulo(cs, fb, "RELATÓRIO TRIBUTAÇÃO MONOFÁSICA PIS/COFINS", periodo, y);
                y -= 8;

                // Nota explicativa
                fill(cs, M, y - 38, PW - 2 * M, 40, new Color(255, 251, 235));
                borda(cs, M, y - 38, PW - 2 * M, 40, new Color(253, 230, 138));
                txt(cs, fn, 8, new Color(120, 53, 15), M + 8, y - 6,
                    "ℹ️  Produtos monofásicos possuem PIS/COFINS recolhido na fabricação (alíquota 0% na revenda).");
                txt(cs, fn, 8, new Color(120, 53, 15), M + 8, y - 18,
                    "     Verificar CST 04 (compras) — sem crédito de PIS/COFINS para o revendedor.");
                txt(cs, fn, 8, new Color(120, 53, 15), M + 8, y - 30,
                    "     Análogo aos registros M410/M810 do SPED Contribuições.");
                y -= 48;

                // Totais
                BigDecimal totalMonofasico = itens.stream()
                    .map(ItemMonofasico::valorTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal pisST = itens.stream()
                    .map(ItemMonofasico::pisValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal cofinsST = itens.stream()
                    .map(ItemMonofasico::cofinsValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                y = boxResumo(cs, fn, fb, new String[][]{
                    {"Itens com tributação monofásica",  String.valueOf(itens.size())},
                    {"Valor total dos itens monofásicos", "R$ " + fmt(totalMonofasico)},
                    {"PIS retido (ST concentrada)",       "R$ " + fmt(pisST)},
                    {"COFINS retido (ST concentrada)",    "R$ " + fmt(cofinsST)},
                    {"CST PIS/COFINS na revenda",         "04 (tributado ST)"},
                }, y);
                y -= 15;

                if (!itens.isEmpty()) {
                    y = sec(cs, fb, "PRODUTOS COM TRIBUTAÇÃO MONOFÁSICA NO PERÍODO", y);
                    String[] hdr = {"Código", "Descrição do Produto", "NCM", "CST", "Qtd", "Valor Total", "PIS ST", "COFINS ST"};
                    float[] ws  = {50, 130, 55, 30, 40, 70, 60, 65};
                    y = cabecTbl(cs, fb, hdr, ws, y);

                    int lc = 0;
                    BigDecimal[] tots = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                    for (ItemMonofasico item : itens) {
                        if (y < M + 20) break;
                        tots[0] = tots[0].add(item.valorTotal());
                        tots[1] = tots[1].add(item.pisValor());
                        tots[2] = tots[2].add(item.cofinsValor());
                        y = linhaTabela(cs, fn, new String[]{
                            item.codigo(), trunc(item.descricao(), 24),
                            item.ncm(), item.cst(),
                            fmt2(item.quantidade()),
                            "R$ " + fmt(item.valorTotal()),
                            "R$ " + fmt(item.pisValor()),
                            "R$ " + fmt(item.cofinsValor())
                        }, ws, y, lc++ % 2 == 0);
                    }
                    // Total
                    linhaTabela(cs, fb, new String[]{
                        "TOTAL", "", "", "", "",
                        "R$ " + fmt(tots[0]),
                        "R$ " + fmt(tots[1]),
                        "R$ " + fmt(tots[2])
                    }, ws, y, false);
                } else {
                    txt(cs, fn, 10, new Color(107, 114, 128), M, y - 20,
                        "Nenhum produto monofásico identificado no período.");
                }

                rodape(cs, fn, 1);
            }

            doc.save(pdf.toFile());
            log.info("PDF Monofásico gerado: {}", pdf);
        }
    }

    private List<ItemMonofasico> buscarItensMonofasicos(Connection conn, Periodo periodo) {
        List<ItemMonofasico> itens = new ArrayList<>();
        String sql = """
            SELECT d.ID, d.CODIGO_PRODUTO, d.DESCRICAO, d.NCM,
                   d.ICMS_CST, d.QUANTIDADE, d.TOTAL_ITEM,
                   d.PIS_VALOR, d.COFINS_VALOR
            FROM NOTA_COMPRA_DETALHE d
            INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID
            WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ?
            AND (d.ICMS_CST IN ('04','104','204') OR d.ICMS_CST LIKE '%04')
            ORDER BY d.NCM, d.CODIGO_PRODUTO
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    itens.add(new ItemMonofasico(
                        s(rs, "CODIGO_PRODUTO"),
                        s(rs, "DESCRICAO"),
                        s(rs, "NCM"),
                        s(rs, "ICMS_CST"),
                        rs.getBigDecimal("QUANTIDADE"),
                        rs.getBigDecimal("TOTAL_ITEM"),
                        rs.getBigDecimal("PIS_VALOR"),
                        rs.getBigDecimal("COFINS_VALOR")
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Busca monofásico via CST04: {}", e.getMessage());
        }

        // Se não encontrou por CST, tentar por NCM
        if (itens.isEmpty()) {
            itens.addAll(buscarPorNcm(conn, periodo));
        }
        return itens;
    }

    private List<ItemMonofasico> buscarPorNcm(Connection conn, Periodo periodo) {
        List<ItemMonofasico> itens = new ArrayList<>();
        String ncmFilter = NCM_MONOFASICO.stream()
            .map(n -> "d.NCM LIKE '" + n + "%'")
            .reduce((a, b) -> a + " OR " + b)
            .orElse("1=0");
        String sql = "SELECT d.CODIGO_PRODUTO, d.DESCRICAO, d.NCM, d.ICMS_CST, " +
                     "d.QUANTIDADE, d.TOTAL_ITEM, d.PIS_VALOR, d.COFINS_VALOR " +
                     "FROM NOTA_COMPRA_DETALHE d INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID " +
                     "WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ? AND (" + ncmFilter + ") " +
                     "ORDER BY d.NCM ROWS 200";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    itens.add(new ItemMonofasico(
                        s(rs, "CODIGO_PRODUTO"), s(rs, "DESCRICAO"), s(rs, "NCM"),
                        s(rs, "ICMS_CST"), rs.getBigDecimal("QUANTIDADE"),
                        rs.getBigDecimal("TOTAL_ITEM"),
                        rs.getBigDecimal("PIS_VALOR"), rs.getBigDecimal("COFINS_VALOR")));
                }
            }
        } catch (Exception e) {
            log.debug("Busca monofásico por NCM: {}", e.getMessage());
        }
        return itens;
    }

    record ItemMonofasico(String codigo, String descricao, String ncm, String cst,
                           BigDecimal quantidade, BigDecimal valorTotal,
                           BigDecimal pisValor, BigDecimal cofinsValor) {}

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
        for (String[] r : rows) { txt(cs, fn, 9, new Color(17, 24, 39), M + 12, ly, r[0]); txt(cs, fb, 9, new Color(17, 24, 39), PW - M - 110, ly, r[1]); ly -= LH; }
        return y - h - 5;
    }
    private float cabecTbl(PDPageContentStream cs, PDFont fb, String[] hdrs, float[] ws, float y) throws IOException {
        fill(cs, M, y - LH, sum(ws), LH + 4, new Color(26, 86, 219));
        cs.beginText(); cs.setFont(fb, 7); cs.setNonStrokingColor(Color.WHITE);
        float cx = M + 4;
        for (int i = 0; i < hdrs.length; i++) { cs.newLineAtOffset(i == 0 ? cx : ws[i - 1], 0); cs.showText(trunc(hdrs[i], (int)(ws[i] / 5))); cx += ws[i]; }
        cs.endText(); cs.setNonStrokingColor(Color.BLACK);
        return y - LH - 4;
    }
    private float linhaTabela(PDPageContentStream cs, PDFont fn, String[] cells, float[] ws, float y, boolean par) throws IOException {
        if (par) fill(cs, M, y - LH + 2, sum(ws), LH, new Color(243, 244, 246));
        cs.beginText(); cs.setFont(fn, 7); cs.setNonStrokingColor(new Color(17, 24, 39));
        float cx = M + 4;
        for (int i = 0; i < cells.length; i++) { cs.newLineAtOffset(i == 0 ? cx : ws[i - 1], 0); cs.showText(trunc(cells[i] != null ? cells[i] : "", (int)(ws[i] / 5))); cx += ws[i]; }
        cs.endText(); cs.setNonStrokingColor(Color.BLACK);
        return y - LH;
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
    private String fmt2(BigDecimal v) {
        if (v == null) return "0";
        return String.format("%,.4f", v).replace(",", "X").replace(".", ",").replace("X", ".");
    }
    private String fmtCnpj(String c) {
        if (c == null || c.length() != 14) return c != null ? c : "";
        return c.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }
    private String s(ResultSet rs, String col) { try { String v = rs.getString(col); return v != null ? v.trim() : ""; } catch (Exception e) { return ""; } }
    private PDFont loadFont(PDDocument doc, boolean b) {
        String res = b ? "/fonts/NotoSans-Bold.ttf" : "/fonts/NotoSans-Regular.ttf";
        try (InputStream is = getClass().getResourceAsStream(res)) { if (is != null) return PDType0Font.load(doc, is, true); } catch (Exception ignored) {}
        try { return PDType0Font.load(doc, new File(b ? "C:/Windows/Fonts/arialbd.ttf" : "C:/Windows/Fonts/arial.ttf"), true); } catch (Exception ignored) {}
        return b ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
    }
}
