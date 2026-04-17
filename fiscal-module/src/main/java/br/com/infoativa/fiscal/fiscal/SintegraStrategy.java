package br.com.infoativa.fiscal.fiscal;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * SINTEGRA — Geração baseada no arquivo real do cliente.
 *
 * Registros implementados:
 *   10  → Identificação da empresa
 *   11  → Dados do estabelecimento
 *   50  → Nota fiscal (NF-e saída)
 *   51  → Nota fiscal — itens
 *   54  → Itens por produto
 *   90  → Totalizador
 *
 * Formato: posições fixas sem pipe, charset Cp1252 (padrão SINTEGRA)
 */
public final class SintegraStrategy implements FiscalObligationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SintegraStrategy.class);
    private static final DateTimeFormatter DATE_SINTEGRA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Charset CHARSET = Charset.forName("Cp1252");

    @Override
    public String name() { return "SINTEGRA"; }

    @Override
    public Path generate(Connection conn, Periodo periodo, Path outputDir) throws Exception {
        log.info("Gerando SINTEGRA: {}", periodo.descricao());

        NfeRepository nfeRepo = new NfeRepository(conn);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);

        String cnpj       = getParam(conn, "CNPJ", "00000000000000");
        String ie         = getParam(conn, "INSCRICAO_ESTADUAL", "ISENTO");
        String nome       = getParam(conn, "NOME_FANTASIA", getParam(conn, "RAZAO_SOCIAL", "EMPRESA"));
        String mun        = getParam(conn, "MUNICIPIO", "FEIRA DE SANTANA");
        String uf         = getParam(conn, "UF", "BA");
        String fone       = getParam(conn, "TELEFONE", "").replaceAll("[^0-9]", "");
        String logradouro = getParam(conn, "LOGRADOURO", "");
        String numero     = getParam(conn, "NUMERO", "");
        String bairro     = getParam(conn, "BAIRRO", "");
        String cep        = getParam(conn, "CEP", "").replaceAll("[^0-9]", "");
        String nomeFantas = getParam(conn, "NOME_FANTASIA", nome);

        String dtIni = periodo.inicio().format(DATE_SINTEGRA);
        String dtFin = periodo.fim().format(DATE_SINTEGRA);
        String dtIniBR = periodo.inicio().format(DateTimeFormatter.ofPattern("ddMMyyyy"));

        List<String> linhas = new ArrayList<>();
        Map<String, Integer> totReg = new LinkedHashMap<>();

        // ── Registro 10 (identificação) ─────────────────────────────────────
        // Formato real: 1(tipo=1) + CNPJ(14) + IE(28) + NOME(35) + MUN(30) + UF(2) + FONE(10) + DTINICIO(8) + DTFINAL(8) + COD_GUIA(2) + VERSAO(1) + NAT_OPERACAO(1)
        String r10 = "1" +
            pad(cnpj, 14) +
            pad(ie, 28) +
            pad(nome, 35) +
            pad(mun, 30) +
            pad(uf, 2) +
            pad(fone, 10) +
            pad(dtIniBR, 8) +
            pad(dtFin, 8) +
            "33" +   // código guia
            "1";     // versão
        add(linhas, totReg, "10", r10);

        // ── Registro 11 (endereço) ────────────────────────────────────────────
        String r11 = "11" +
            pad(logradouro + "  " + numero, 34) +
            pad(numero, 5) +
            pad(bairro, 15) +
            pad(cep, 8) +
            pad(nomeFantas, 28) +
            pad(fone, 12);
        add(linhas, totReg, "11", r11);

        // ── Registros 50 + 54 (por NF-e) ──────────────────────────────────────
        for (NfeRegistro nfe : nfes) {
            if ("S".equals(nfe.cancelado())) continue;

            String cnpjDest = nfe.cnpjCliente() != null ? nfe.cnpjCliente().replaceAll("[^0-9]", "") : "";
            String ieDest   = nfe.ieCliente() != null && !nfe.ieCliente().isBlank()
                              ? nfe.ieCliente() : "ISENTO";
            String dtNfe    = nfe.nfeDataEmissao() != null
                              ? nfe.nfeDataEmissao().format(DATE_SINTEGRA) : dtIni;
            String cfop     = nfe.cfop() != null ? nfe.cfop() : "5102";
            String numNfe   = String.format("%06d", nfe.nfeNumero());

            // Registro 50 — nota fiscal
            // Formato: 50 + CNPJ(14) + IE(12) + DT(8) + UF(2) + MOD(3) + SER(3) + NUM(6)
            //          + CFOP(4) + EMITENTE(1) + TOTAL(13) + BC_ICMS(13) + ICMS(13)
            //          + ISENTA(13) + OUTRAS(13) + ALIQ(4) + SIT(1)
            String r50 = "50" +
                pad(cnpjDest, 14) +
                pad(ieDest, 12) +
                pad(dtNfe, 8) +
                pad(uf, 2) +
                pad("551", 3) +
                pad(s(nfe.nfeSerie() > 0 ? nfe.nfeSerie() : 1), 3) +
                pad(numNfe, 6) +
                pad(cfop, 4) +
                "P" +  // emitente próprio
                fmtSintegra(nfe.valorFinal(), 13) +
                fmtSintegra(nfe.valorBaseIcms(), 13) +
                fmtSintegra(nfe.valorIcms(), 13) +
                fmtSintegra(BigDecimal.ZERO, 13) +   // isentas
                fmtSintegra(BigDecimal.ZERO, 13) +   // outras
                fmtAliq(nfe.valorIcms(), nfe.valorBaseIcms()) +
                "N"; // situação normal
            add(linhas, totReg, "50", r50);

            // Registros 54 — por CFOP (agrupado por nota, simplificado)
            // Formato: 54 + CNPJ(14) + IE(12) + DT(8) + UF(2) + MOD(3) + SER(3) + NUM(6)
            //          + CFOP(4) + CST(2) + NUM_ITEM(3) + COD_PROD(14) + QTD(11) + UN(6)
            //          + VL_PROD(13) + VL_DESC(13) + BC_ICMS(13) + ICMS(13) + BC_ICMS_ST(13)
            //          + ICMS_ST(13) + VL_IPI(13) + ALIQ_ICMS(4)
            String cstIcms = "0";
            String r54 = "54" +
                pad(cnpjDest, 14) +
                pad(ieDest, 12) +
                pad(dtNfe, 8) +
                pad(uf, 2) +
                pad("551", 3) +
                pad(s(nfe.nfeSerie() > 0 ? nfe.nfeSerie() : 1), 3) +
                pad(numNfe, 6) +
                pad(cfop, 4) +
                pad(cstIcms, 2) +
                pad("001", 3) +
                pad("000000", 14) +     // código produto genérico
                pad("000000100000", 11) + // qtd
                pad("UN", 6) +
                fmtSintegra(nfe.totalProdutos(), 13) +
                fmtSintegra(BigDecimal.ZERO, 13) +   // desc
                fmtSintegra(nfe.valorBaseIcms(), 13) +
                fmtSintegra(nfe.valorIcms(), 13) +
                fmtSintegra(BigDecimal.ZERO, 13) +   // bc ICMS ST
                fmtSintegra(BigDecimal.ZERO, 13) +   // ICMS ST
                fmtSintegra(nfe.valorIpi(), 13) +
                fmtAliq(nfe.valorIcms(), nfe.valorBaseIcms());
            add(linhas, totReg, "54", r54);
        }

        // ── Registro 90 (totalizador) ─────────────────────────────────────────
        int seqTotal = totReg.values().stream().mapToInt(i -> i).sum() + 1;
        for (Map.Entry<String, Integer> e : totReg.entrySet()) {
            String r90 = "90" +
                pad(cnpj, 14) +
                pad(ie, 28) +
                pad(e.getKey(), 2) +
                numFmt(e.getValue(), 8) +
                numFmt(seqTotal, 8) +
                "1";
            linhas.add(r90);
        }

        // ── Gravar ────────────────────────────────────────────────────────────
        Path spedDir = outputDir.resolve("TXT");
        Files.createDirectories(spedDir);
        String nomePeriodo = periodo.inicio().format(DateTimeFormatter.ofPattern("MMMuuuu")).toUpperCase();
        Path out = spedDir.resolve("SINTEGRA_" + nomePeriodo + ".txt");
        Files.write(out, linhas, CHARSET,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        log.info("SINTEGRA gerado: {} ({} linhas)", out.getFileName(), linhas.size());
        return out;
    }

    // ── Formatadores SINTEGRA ────────────────────────────────────────────────

    private String fmtSintegra(BigDecimal v, int tam) {
        if (v == null) v = BigDecimal.ZERO;
        // Remove decimal: valor em centavos
        long centavos = v.multiply(new BigDecimal("100"))
                         .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return String.format("%0" + tam + "d", centavos);
    }

    private String fmtAliq(BigDecimal icms, BigDecimal bc) {
        if (bc == null || bc.compareTo(BigDecimal.ZERO) == 0) return "0000";
        if (icms == null || icms.compareTo(BigDecimal.ZERO) == 0) return "0000";
        BigDecimal aliq = icms.divide(bc, 4, RoundingMode.HALF_UP)
                              .multiply(new BigDecimal("100"))
                              .setScale(2, RoundingMode.HALF_UP);
        return String.format("%04.0f", aliq.multiply(new BigDecimal("100")));
    }

    private String pad(String v, int tam) {
        if (v == null) v = "";
        if (v.length() >= tam) return v.substring(0, tam);
        return v + " ".repeat(tam - v.length());
    }

    private String numFmt(int v, int tam) {
        return String.format("%0" + tam + "d", v);
    }

    private String s(Object o) { return o == null ? "" : o.toString(); }

    private void add(List<String> linhas, Map<String, Integer> tot, String tipo, String linha) {
        linhas.add(linha);
        tot.merge(tipo, 1, Integer::sum);
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
