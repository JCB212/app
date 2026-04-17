package br.com.infoativa.fiscal.fiscal;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * SPED EFD Fiscal — Geração completa baseada no layout real do cliente.
 *
 * Blocos implementados:
 *   0 → Abertura, empresa, participantes (0000/0001/0005/0100/0150/0990)
 *   B → Escrituração de serviços (zerado — padrão comércio)
 *   C → NF-e saída (C100 + C190 por CST/CFOP)
 *   D → NF transporte/serviço (zerado)
 *   E → Apuração ICMS (E100/E110)
 *   G → Diferimento ICMS ativo (zerado)
 *   H → Inventário (zerado)
 *   K → Controle da produção (zerado)
 *   9 → Encerramento + 9900 (qtd por registro) + 9999
 *
 * Formato de campos: valores decimais com vírgula (padrão SPED)
 */
public final class SpedFiscalStrategy implements FiscalObligationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SpedFiscalStrategy.class);
    private static final DateTimeFormatter SPED_DATE = DateTimeFormatter.ofPattern("ddMMyyyy");

    @Override
    public String name() { return "SPED_FISCAL"; }

    @Override
    public Path generate(Connection conn, Periodo periodo, Path outputDir) throws Exception {
        log.info("Gerando SPED Fiscal: {}", periodo.descricao());

        NfeRepository nfeRepo = new NfeRepository(conn);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);

        log.info("NFe encontradas para SPED Fiscal: {}", nfes.size());

        // Buscar dados da empresa via parâmetros
        String cnpj        = getParam(conn, "CNPJ", "");
        String nomeEmp     = getParam(conn, "NOME_FANTASIA", getParam(conn, "RAZAO_SOCIAL", "EMPRESA"));
        String ie          = getParam(conn, "INSCRICAO_ESTADUAL", "");
        String im          = getParam(conn, "INSCRICAO_MUNICIPAL", "");
        String uf          = getParam(conn, "UF", "BA");
        String codMun      = getParam(conn, "CODIGO_MUNICIPIO", "2910800");
        String suframa     = getParam(conn, "SUFRAMA", "");
        String contato     = getParam(conn, "CONTATO_FISCAL", "");
        String fone        = getParam(conn, "TELEFONE", "");
        String email       = getParam(conn, "EMAIL_FISCAL", "");
        String logradouro  = getParam(conn, "LOGRADOURO", "");
        String numero      = getParam(conn, "NUMERO", "");
        String bairro      = getParam(conn, "BAIRRO", "");
        String cep         = getParam(conn, "CEP", "");
        String regTrib     = getParam(conn, "REGIME_TRIBUTARIO", "1"); // 1=SN, 2=LP, 3=LR
        String nomeContab  = getParam(conn, "NOME_CONTABILISTA", contato);

        // ── Construir arquivo ──────────────────────────────────────────────
        List<String> linhas = new ArrayList<>();
        Map<String, Integer> contadores = new LinkedHashMap<>();

        String dtIni = periodo.inicio().format(SPED_DATE);
        String dtFin = periodo.fim().format(SPED_DATE);

        // ── BLOCO 0 ────────────────────────────────────────────────────────
        add(linhas, contadores, reg("0000",
            "019", "0", dtIni, dtFin, nomeEmp, cnpj, suframa, uf, ie, codMun,
            im, suframa, regTrib, "1"));
        add(linhas, contadores, reg("0001", "0"));
        add(linhas, contadores, reg("0005",
            nomeEmp, cep, logradouro, numero, "", bairro, fone, fone, email));
        add(linhas, contadores, reg("0100",
            nomeContab, "", "", "", "", "", "", "", "", fone, "", email, codMun));

        // Participantes (clientes com NFe no período)
        Set<String> partsCod = new LinkedHashSet<>();
        for (NfeRegistro nfe : nfes) {
            String cod = "C" + String.format("%06d", nfe.idCliente());
            if (partsCod.add(cod)) {
                add(linhas, contadores, reg("0150",
                    cod, nfe.nomeCliente() != null ? nfe.nomeCliente() : "",
                    "01058",
                    nfe.cnpjCliente() != null ? nfe.cnpjCliente() : "",
                    nfe.cpfCliente() != null ? nfe.cpfCliente() : "",
                    nfe.ieCliente() != null ? nfe.ieCliente() : "",
                    codMun, "", "", "", "", ""));
            }
        }

        add(linhas, contadores, reg("0990", String.valueOf(
            contadores.values().stream().mapToInt(i -> i).sum() + 1)));

        // ── BLOCO B (zerado) ────────────────────────────────────────────────
        add(linhas, contadores, reg("B001", "1"));
        add(linhas, contadores, reg("B990", "2"));

        // ── BLOCO C (NF-e saída) ────────────────────────────────────────────
        add(linhas, contadores, reg("C001", "0"));

        for (NfeRegistro nfe : nfes) {
            String codPart = "C" + String.format("%06d", nfe.idCliente());
            String codSit  = nfe.cancelado() != null && "S".equals(nfe.cancelado()) ? "02" : "00";
            String dtDoc   = nfe.nfeDataEmissao() != null ? nfe.nfeDataEmissao().format(SPED_DATE) : dtIni;

            add(linhas, contadores, reg("C100",
                "1", "0", codPart,
                str(nfe.nfeModelo() > 0 ? nfe.nfeModelo() : 55),
                codSit, "1",
                String.format("%09d", nfe.nfeNumero()),
                nfe.nfeChaveAcesso() != null ? nfe.nfeChaveAcesso() : "",
                dtDoc, dtDoc,
                fmt(nfe.valorFinal()), "0",
                fmt(nfe.desconto()), "0,00",
                fmt(nfe.totalProdutos()), "0",
                fmt(nfe.valorFrete()), fmt(nfe.valorSeguro()), "0,00",
                fmt(nfe.valorBaseIcms()), fmt(nfe.valorIcms()),
                "0,00", "0,00",
                fmt(nfe.valorIpi()),
                fmt(nfe.valorPis()), fmt(nfe.valorCofins()),
                "0,00", "0,00"));

            // C190: linha por combinação CST+CFOP (agrupa por CFOP da nota)
            String cfop = nfe.cfop() != null && !nfe.cfop().isBlank()
                          ? nfe.cfop() : "5102";

            // CST ICMS baseado no regime tributário
            String cstIcms = "1".equals(regTrib) ? "500" :  // Simples Nacional
                             "102";                          // Normal (isento)

            add(linhas, contadores, reg("C190",
                cstIcms, cfop, "0,00",
                fmt(nfe.totalProdutos()),
                fmt(nfe.valorBaseIcms()), fmt(nfe.valorIcms()),
                "0,00", "0,00", "0,00",
                fmt(nfe.valorIpi()), ""));
        }

        add(linhas, contadores, reg("C990",
            String.valueOf(contadores.getOrDefault("C100", 0)
                + contadores.getOrDefault("C190", 0) + 2)));

        // ── BLOCO D (zerado) ────────────────────────────────────────────────
        add(linhas, contadores, reg("D001", "1"));
        add(linhas, contadores, reg("D990", "2"));

        // ── BLOCO E (apuração ICMS) ─────────────────────────────────────────
        BigDecimal totalIcms = nfes.stream()
            .filter(n -> !"S".equals(n.cancelado()))
            .map(NfeRegistro::valorIcms).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBcIcms = nfes.stream()
            .filter(n -> !"S".equals(n.cancelado()))
            .map(NfeRegistro::valorBaseIcms).reduce(BigDecimal.ZERO, BigDecimal::add);

        add(linhas, contadores, reg("E001", "0"));
        add(linhas, contadores, reg("E100", dtIni, dtFin));
        // E110: apuração ICMS (baseado na estrutura real do arquivo)
        add(linhas, contadores, reg("E110",
            fmt(totalBcIcms), "0,00", "0,00", "0,00", "0,00",
            fmt(totalIcms),
            "0,00", "0,00", "0,00", "0,00", "0,00",
            fmt(totalIcms), "0,00", "0,00"));
        add(linhas, contadores, reg("E990",
            String.valueOf(contadores.getOrDefault("E001", 0)
                + contadores.getOrDefault("E100", 0)
                + contadores.getOrDefault("E110", 0) + 2)));

        // ── BLOCO G (zerado) ────────────────────────────────────────────────
        add(linhas, contadores, reg("G001", "1"));
        add(linhas, contadores, reg("G990", "2"));

        // ── BLOCO H (inventário — zerado) ───────────────────────────────────
        add(linhas, contadores, reg("H001", "1"));
        add(linhas, contadores, reg("H990", "2"));

        // ── BLOCO K (produção — zerado) ──────────────────────────────────────
        add(linhas, contadores, reg("K001", "1"));
        add(linhas, contadores, reg("K990", "2"));

        // ── BLOCO 1 (complementar — informações analíticas) ─────────────────
        add(linhas, contadores, reg("1001", "1"));
        add(linhas, contadores, reg("1010",
            "N", "N", "N", "N", "N", "N", "N", "N", "N", "N",
            "N", "N", "N", "N", "N", "N", "N", "N"));
        add(linhas, contadores, reg("1990", "3"));

        // ── BLOCO 9 (encerramento) ───────────────────────────────────────────
        add(linhas, contadores, reg("9001", "0"));

        // 9900 — quantidade por tipo de registro
        for (Map.Entry<String, Integer> e : contadores.entrySet()) {
            add(linhas, contadores, reg("9900", e.getKey(), str(e.getValue())));
        }

        int total = linhas.size() + 2; // +9990 +9999
        add(linhas, contadores, reg("9990", str(total)));
        add(linhas, contadores, reg("9999", str(linhas.size() + 1)));

        // ── Gravar arquivo ────────────────────────────────────────────────────
        Path spedDir = outputDir.resolve("TXT");
        Files.createDirectories(spedDir);
        Path out = spedDir.resolve("SPED_FISCAL_" + dtIni + "_" + dtFin + ".txt");
        Files.write(out, linhas, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        log.info("SPED Fiscal gerado: {} ({} linhas)", out.getFileName(), linhas.size());
        return out;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String reg(String tipo, String... campos) {
        StringBuilder sb = new StringBuilder("|").append(tipo).append("|");
        for (int i = 0; i < campos.length; i++) {
            sb.append(campos[i] != null ? campos[i] : "");
            if (i < campos.length - 1) sb.append("|");
        }
        sb.append("|");
        return sb.toString();
    }

    private void add(List<String> linhas, Map<String, Integer> cnt, String linha) {
        linhas.add(linha);
        // Extrair tipo do registro (entre primeiro e segundo pipe)
        String tipo = linha.substring(1, linha.indexOf('|', 1));
        cnt.merge(tipo, 1, Integer::sum);
    }

    private String fmt(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) == 0) return "0,00";
        return String.format("%.2f", v).replace(".", ",");
    }

    private String str(Object v) { return v == null ? "" : v.toString(); }

    private String getParam(Connection conn, String campo, String def) {
        for (String tbl : new String[]{"PARAMETROS", "EMPRESA", "CONFIGURACOES"}) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT " + campo + " FROM " + tbl + " ROWS 1")) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && !v.isBlank()) return v.trim();
                }
            } catch (Exception ignored) {}
        }
        return def;
    }
}
