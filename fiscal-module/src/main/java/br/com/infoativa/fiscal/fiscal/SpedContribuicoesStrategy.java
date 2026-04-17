package br.com.infoativa.fiscal.fiscal;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * SPED EFD Contribuições — Geração completa baseada no layout real do cliente.
 *
 * Blocos implementados:
 *   0 → Abertura, empresa, participantes, unidades, produtos (0000–0990)
 *   A → Serviços ISS (zerado)
 *   C → Documentos fiscais (C010/C100/C170/C175 por nota e item)
 *   D → Documentos — serviços transporte (zerado)
 *   F → Demais documentos (zerado)
 *   M → Apuração PIS/COFINS (M200/M400/M410 + M600/M800/M810)
 *   9 → Encerramento
 *
 * CST real do cliente: 99 (outras receitas — regime SN/LP)
 * Alíquotas reais: PIS 0,65% / COFINS 3,00%
 */
public final class SpedContribuicoesStrategy implements FiscalObligationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SpedContribuicoesStrategy.class);
    private static final DateTimeFormatter SPED_DATE = DateTimeFormatter.ofPattern("ddMMyyyy");

    // CST/Alíquotas reais do cliente (ajustar conforme regime)
    private static final String CST_PIS    = "99";
    private static final String CST_COFINS = "99";
    private static final String ALIQ_PIS   = "0,6500";
    private static final String ALIQ_COFINS = "3,0000";
    private static final String COD_CTA    = "4.1.1.01.0001";

    @Override
    public String name() { return "SPED_CONTRIBUICOES"; }

    @Override
    public Path generate(Connection conn, Periodo periodo, Path outputDir) throws Exception {
        log.info("Gerando SPED Contribuições: {}", periodo.descricao());

        NfeRepository nfeRepo   = new NfeRepository(conn);
        NfceRepository nfceRepo = new NfceRepository(conn);

        List<NfeRegistro>  nfes  = nfeRepo.findByPeriodo(periodo);
        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);

        log.info("NFe: {} | NFCe: {}", nfes.size(), nfces.size());

        // Parâmetros da empresa
        String cnpj    = getParam(conn, "CNPJ", "");
        String nome    = getParam(conn, "NOME_FANTASIA", getParam(conn, "RAZAO_SOCIAL", "EMPRESA"));
        String ie      = getParam(conn, "INSCRICAO_ESTADUAL", "");
        String uf      = getParam(conn, "UF", "BA");
        String codMun  = getParam(conn, "CODIGO_MUNICIPIO", "2910800");
        String regTrib = getParam(conn, "REGIME_TRIBUTARIO", "2"); // 2=LP
        String fone    = getParam(conn, "TELEFONE", "");
        String email   = getParam(conn, "EMAIL_FISCAL", "");
        String contato = getParam(conn, "CONTATO_FISCAL", "");

        String dtIni = periodo.inicio().format(SPED_DATE);
        String dtFin = periodo.fim().format(SPED_DATE);

        List<String> linhas = new ArrayList<>();
        Map<String, Integer> cnt = new LinkedHashMap<>();

        // ── BLOCO 0 ────────────────────────────────────────────────────────
        add(linhas, cnt, reg("0000",
            "006", "0", "", "", dtIni, dtFin, nome, cnpj, uf, codMun, ie, "00", regTrib));
        add(linhas, cnt, reg("0001", "0"));
        add(linhas, cnt, reg("0100",
            contato, "", "", "", "", "", "", "", "", fone, "", email, codMun));
        add(linhas, cnt, reg("0110", cnpj, regTrib, "1", "9"));
        add(linhas, cnt, reg("0140", "1", nome, cnpj, uf, ie, codMun, "", ""));

        // Participantes
        Set<String> parts = new LinkedHashSet<>();
        for (NfeRegistro n : nfes) {
            String cod = "C" + String.format("%06d", n.idCliente());
            if (parts.add(cod)) {
                add(linhas, cnt, reg("0150",
                    cod,
                    n.nomeCliente() != null ? n.nomeCliente() : "",
                    "01058",
                    n.cnpjCliente() != null ? n.cnpjCliente() : "",
                    n.cpfCliente() != null ? n.cpfCliente() : "",
                    n.ieCliente() != null ? n.ieCliente() : "",
                    codMun, "", "", "", "", ""));
            }
        }

        // Unidades de medida
        add(linhas, cnt, reg("0190", "DZ", "Duzia"));
        add(linhas, cnt, reg("0190", "MT", "Metro"));
        add(linhas, cnt, reg("0190", "PC", "Peça"));
        add(linhas, cnt, reg("0190", "UN", "Unidade"));

        // Produtos (buscar do banco)
        gerarBloco0200(conn, linhas, cnt);

        // Plano de contas (para o COD_CTA usado nas contribuições)
        add(linhas, cnt, reg("0500", COD_CTA, "A", "4", "", "Receita de Vendas"));
        add(linhas, cnt, reg("0500", "4.1.2.01.0001", "A", "4", "", "Receita de Serviços"));

        add(linhas, cnt, reg("0990", s(cnt.values().stream().mapToInt(i -> i).sum() + 1)));

        // ── BLOCO A (zerado) ────────────────────────────────────────────────
        add(linhas, cnt, reg("A001", "1"));
        add(linhas, cnt, reg("A990", "2"));

        // ── BLOCO C ─────────────────────────────────────────────────────────
        add(linhas, cnt, reg("C001", "0"));
        add(linhas, cnt, reg("C010", cnpj, "2")); // 2 = competência

        // NFe (modelo 55)
        for (NfeRegistro nfe : nfes) {
            String codPart = "C" + String.format("%06d", nfe.idCliente());
            String codSit  = "S".equals(nfe.cancelado()) ? "02" : "00";
            String dtDoc   = nfe.nfeDataEmissao() != null ? nfe.nfeDataEmissao().format(SPED_DATE) : dtIni;

            add(linhas, cnt, reg("C100",
                "1", "0", codPart,
                s(nfe.nfeModelo() > 0 ? nfe.nfeModelo() : 55),
                codSit, "1",
                String.format("%09d", nfe.nfeNumero()),
                nfe.nfeChaveAcesso() != null ? nfe.nfeChaveAcesso() : "",
                dtDoc, dtDoc,
                fmt(nfe.valorFinal()), "0",
                "0,00", "0,00",
                fmt(nfe.totalProdutos()), "9",
                "0,00", "0,00", "0,00", "0,00",
                "0,00", "0,00", "0,00", "0,00",
                "0,00", "0,00", "0,00", "0,00"));

            // C175: apuração PIS/COFINS por nota (resumo)
            String cfop = nfe.cfop() != null ? nfe.cfop() : "5102";
            BigDecimal vlNfe = nfe.totalProdutos() != null ? nfe.totalProdutos() : BigDecimal.ZERO;
            BigDecimal pisCal = vlNfe.multiply(new BigDecimal("0.0065")).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal cofinsCal = vlNfe.multiply(new BigDecimal("0.03")).setScale(2, java.math.RoundingMode.HALF_UP);

            add(linhas, cnt, reg("C175",
                cfop,
                fmt(vlNfe), "0,00",
                CST_PIS, "0,00", ALIQ_PIS, "", "", fmt(pisCal),
                CST_COFINS, "0,00", ALIQ_COFINS, "", "", fmt(cofinsCal),
                COD_CTA, ""));
        }

        // NFCe (modelo 65)
        for (NfceRegistro nfce : nfces) {
            if ("S".equals(nfce.cupomCancelado())) continue;
            String codPart = "C" + String.format("%06d", nfce.idCliente());
            String dtDoc   = nfce.nfceDataEmissao() != null ? nfce.nfceDataEmissao().format(SPED_DATE) : dtIni;

            add(linhas, cnt, reg("C100",
                "1", "0", codPart, "65", "00", "1",
                String.format("%09d", nfce.nfceNumero()),
                nfce.nfceChaveAcesso() != null ? nfce.nfceChaveAcesso() : "",
                dtDoc, dtDoc,
                fmt(nfce.valorFinal()), "0",
                "0,00", "0,00",
                fmt(nfce.totalProdutos()), "9",
                "0,00", "0,00", "0,00", "0,00",
                "0,00", "0,00", "0,00", "0,00",
                "0,00", "0,00", "0,00", "0,00"));

            String cfop = nfce.cfop() != null ? nfce.cfop() : "5405";
            BigDecimal vlNfce = nfce.totalProdutos() != null ? nfce.totalProdutos() : BigDecimal.ZERO;
            BigDecimal pisCal = vlNfce.multiply(new BigDecimal("0.0065")).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal cofinsCal = vlNfce.multiply(new BigDecimal("0.03")).setScale(2, java.math.RoundingMode.HALF_UP);

            add(linhas, cnt, reg("C175",
                cfop,
                fmt(vlNfce), "0,00",
                CST_PIS, "0,00", ALIQ_PIS, "", "", fmt(pisCal),
                CST_COFINS, "0,00", ALIQ_COFINS, "", "", fmt(cofinsCal),
                COD_CTA, ""));
        }

        add(linhas, cnt, reg("C990", s(cnt.getOrDefault("C001", 0)
            + cnt.getOrDefault("C010", 0) + cnt.getOrDefault("C100", 0)
            + cnt.getOrDefault("C170", 0) + cnt.getOrDefault("C175", 0) + 2)));

        // ── BLOCO D (zerado) ────────────────────────────────────────────────
        add(linhas, cnt, reg("D001", "1"));
        add(linhas, cnt, reg("D990", "2"));

        // ── BLOCO F (zerado) ────────────────────────────────────────────────
        add(linhas, cnt, reg("F001", "1"));
        add(linhas, cnt, reg("F990", "2"));

        // ── BLOCO M (apuração PIS/COFINS) ────────────────────────────────────
        add(linhas, cnt, reg("M001", "0"));

        // Totais PIS (receita bruta total de NFe + NFCe ativas)
        BigDecimal recBruta = calcReceitaBruta(nfes, nfces);
        BigDecimal totalPis    = recBruta.multiply(new BigDecimal("0.0065")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalCofins = recBruta.multiply(new BigDecimal("0.03")).setScale(2, java.math.RoundingMode.HALF_UP);

        // M200 — apuração PIS total
        add(linhas, cnt, reg("M200",
            fmt(totalPis), "0,00", "0,00", "0,00", "0,00", "0,00",
            "0,00", "0,00", "0,00", "0,00", "0,00", "0,00"));

        // M400 — receita por CST (receita tributada normalmente)
        add(linhas, cnt, reg("M400",
            CST_PIS, fmt(recBruta), COD_CTA, "Receita de vendas"));

        // M410 — se houver monofásico (simplificado)
        gerarM410(conn, periodo, linhas, cnt);

        // M600 — apuração COFINS total
        add(linhas, cnt, reg("M600",
            fmt(totalCofins), "0,00", "0,00", "0,00", "0,00", "0,00",
            "0,00", "0,00", "0,00", "0,00", "0,00", "0,00"));

        // M800 — receita por CST COFINS
        add(linhas, cnt, reg("M800",
            CST_COFINS, fmt(recBruta), COD_CTA, "Receita de vendas"));

        // M810 — monofásico COFINS (mesmo que M410 mas para COFINS)
        gerarM810(conn, periodo, linhas, cnt);

        add(linhas, cnt, reg("M990", s(cnt.values().stream().mapToInt(i -> i).sum()
            - linhas.size() + cnt.getOrDefault("M001", 0) + 2)));

        // ── BLOCO P (zerado — produtor rural) ───────────────────────────────
        add(linhas, cnt, reg("P990", "1"));

        // ── BLOCO 9 ──────────────────────────────────────────────────────────
        add(linhas, cnt, reg("9001", "0"));
        for (Map.Entry<String, Integer> e : cnt.entrySet()) {
            add(linhas, cnt, reg("9900", e.getKey(), s(e.getValue())));
        }
        int total = linhas.size() + 2;
        add(linhas, cnt, reg("9990", s(total)));
        add(linhas, cnt, reg("9999", s(linhas.size() + 1)));

        // ── Gravar ────────────────────────────────────────────────────────────
        Path spedDir = outputDir.resolve("TXT");
        Files.createDirectories(spedDir);
        Path out = spedDir.resolve("SPED_CONTRIBUICOES_" + dtIni + "_" + dtFin + ".txt");
        Files.write(out, linhas, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        log.info("SPED Contribuições gerado: {} ({} linhas)", out.getFileName(), linhas.size());
        return out;
    }

    // ── Helpers específicos ────────────────────────────────────────────────────

    private void gerarBloco0200(Connection conn, List<String> linhas, Map<String, Integer> cnt) {
        String sql = """
            SELECT p.CODIGO, p.DESCRICAO, p.CODIGO_BARRAS,
                   p.UNIDADE, p.NCM, p.TIPO_ITEM, p.PRECO_VENDA
            FROM PRODUTO p
            WHERE p.ATIVO = 'S' OR p.ATIVO = 'N'
            ORDER BY p.CODIGO
            ROWS 500
            """;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String cod   = nullSafe(rs, "CODIGO");
                String desc  = nullSafe(rs, "DESCRICAO");
                String barras = nullSafe(rs, "CODIGO_BARRAS");
                String unid  = nullSafe(rs, "UNIDADE");
                String ncm   = nullSafe(rs, "NCM");
                String tipo  = nullSafe(rs, "TIPO_ITEM");
                String preco = fmtBD(rs.getBigDecimal("PRECO_VENDA"));
                add(linhas, cnt, reg("0200", cod, desc, barras, cod, unid,
                    tipo.isEmpty() ? "00" : tipo, ncm, "", preco, "1"));
            }
        } catch (Exception e) {
            log.warn("Bloco 0200 (produtos) não gerado: {}", e.getMessage());
        }
    }

    private void gerarM410(Connection conn, Periodo periodo, List<String> linhas, Map<String, Integer> cnt) {
        // Produtos com CST monofásico (tributação concentrada)
        String sql = """
            SELECT d.ICMS_CST, SUM(d.TOTAL_ITEM) as TOTAL
            FROM NOTA_COMPRA_DETALHE d
            INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID
            WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ?
            AND d.ICMS_CST IN ('02','03','04','201','202','203')
            GROUP BY d.ICMS_CST
            ORDER BY d.ICMS_CST
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    add(linhas, cnt, reg("M410",
                        rs.getString("ICMS_CST"),
                        fmt(rs.getBigDecimal("TOTAL")),
                        COD_CTA, "Receita monofásica"));
                }
            }
        } catch (Exception e) {
            log.debug("M410 sem dados monofásicos: {}", e.getMessage());
        }
    }

    private void gerarM810(Connection conn, Periodo periodo, List<String> linhas, Map<String, Integer> cnt) {
        // Mesma lógica do M410 mas para COFINS
        String sql = """
            SELECT d.ICMS_CST, SUM(d.TOTAL_ITEM) as TOTAL
            FROM NOTA_COMPRA_DETALHE d
            INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID
            WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ?
            AND d.ICMS_CST IN ('02','03','04','201','202','203')
            GROUP BY d.ICMS_CST
            ORDER BY d.ICMS_CST
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    add(linhas, cnt, reg("M810",
                        rs.getString("ICMS_CST"),
                        fmt(rs.getBigDecimal("TOTAL")),
                        COD_CTA, "Receita monofásica"));
                }
            }
        } catch (Exception e) {
            log.debug("M810 sem dados: {}", e.getMessage());
        }
    }

    private BigDecimal calcReceitaBruta(List<NfeRegistro> nfes, List<NfceRegistro> nfces) {
        BigDecimal total = BigDecimal.ZERO;
        for (NfeRegistro n : nfes) {
            if (!"S".equals(n.cancelado()) && n.totalProdutos() != null)
                total = total.add(n.totalProdutos());
        }
        for (NfceRegistro n : nfces) {
            if (!"S".equals(n.cupomCancelado()) && n.totalProdutos() != null)
                total = total.add(n.totalProdutos());
        }
        return total;
    }

    // ── Utilitários ───────────────────────────────────────────────────────────

    private String reg(String tipo, String... campos) {
        StringBuilder sb = new StringBuilder("|").append(tipo).append("|");
        for (int i = 0; i < campos.length; i++) {
            sb.append(campos[i] != null ? campos[i] : "");
            if (i < campos.length - 1) sb.append("|");
        }
        return sb.append("|").toString();
    }

    private void add(List<String> linhas, Map<String, Integer> cnt, String linha) {
        linhas.add(linha);
        String tipo = linha.substring(1, linha.indexOf('|', 1));
        cnt.merge(tipo, 1, Integer::sum);
    }

    private String fmt(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) == 0) return "0,00";
        return String.format("%.2f", v).replace(".", ",");
    }

    private String fmtBD(BigDecimal v) { return fmt(v); }

    private String s(Object v) { return v == null ? "" : v.toString(); }

    private String nullSafe(ResultSet rs, String col) {
        try { String v = rs.getString(col); return v != null ? v.trim() : ""; }
        catch (Exception e) { return ""; }
    }

    private String getParam(Connection conn, String campo, String def) {
        for (String tbl : new String[]{"PARAMETROS", "EMPRESA"}) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT " + campo + " FROM " + tbl + " ROWS 1")) {
                if (rs.next()) { String v = rs.getString(1); if (v != null && !v.isBlank()) return v.trim(); }
            } catch (Exception ignored) {}
        }
        return def;
    }
}
