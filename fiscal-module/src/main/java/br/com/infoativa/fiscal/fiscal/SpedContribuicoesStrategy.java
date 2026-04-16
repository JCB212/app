package br.com.infoativa.fiscal.fiscal;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SPED Contribuicoes (EFD PIS/COFINS) - Leiaute versao 020 (2026)
 * Versao do leiaute: ano - 2006 = versao (2026=20, 2027=21, etc.)
 * Baseado no arquivo real do cliente com C100/C170/C175, M200/M400/M600/M800.
 */
public class SpedContribuicoesStrategy implements FiscalObligationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SpedContribuicoesStrategy.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private final AtomicInteger lineCount = new AtomicInteger(0);
    private final Map<String, Integer> regCount = new LinkedHashMap<>();

    @Override
    public String name() { return "SPED_CONTRIBUICOES"; }

    @Override
    public void generate(Connection conn, Periodo periodo, Path outputDir) throws Exception {
        Path file = outputDir.resolve("TXT").resolve("SPED_CONTRIBUICOES_" + periodo.mesAnoRef() + ".txt");
        lineCount.set(0);
        regCount.clear();

        // Calculate version: year - 2006
        int versaoLeiaute = periodo.inicio().getYear() - 2006;

        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);
        CompraRepository compraRepo = new CompraRepository(conn);

        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);
        List<CompraRegistro> compras = compraRepo.findByPeriodo(periodo);

        String nomeEmpresa = "";
        String cnpj = "";
        String uf = "BA";
        String codMun = "";
        String ie = "";
        try {
            ParametrosRepository paramRepo = new ParametrosRepository(conn);
            nomeEmpresa = paramRepo.findNomeEmpresa();
            ParametrosRegistro params = paramRepo.findFirst();
            if (params != null) uf = params.nfeWebserviceUf().isEmpty() ? "BA" : params.nfeWebserviceUf();
            if (!nfes.isEmpty() && nfes.get(0).nfeChaveAcesso() != null && nfes.get(0).nfeChaveAcesso().length() >= 25) {
                cnpj = nfes.get(0).nfeChaveAcesso().substring(6, 20);
            } else if (!nfces.isEmpty() && nfces.get(0).nfceChaveAcesso() != null && nfces.get(0).nfceChaveAcesso().length() >= 25) {
                cnpj = nfces.get(0).nfceChaveAcesso().substring(6, 20);
            }
        } catch (Exception ignored) {}

        BigDecimal totalPisVendas = BigDecimal.ZERO;
        BigDecimal totalCofinsVendas = BigDecimal.ZERO;
        BigDecimal totalBaseVendas = BigDecimal.ZERO;

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            // ===== BLOCO 0 =====
            wl(w, "|0000|006|0|||" + fmt(periodo.inicio()) + "|" + fmt(periodo.fim()) + "|" + nomeEmpresa + "|" + cnpj + "|" + uf + "|" + codMun + "||00|2|");
            wl(w, "|0001|0|");
            wl(w, "|0100|||||00000000||||||||0000000|");
            wl(w, "|0110|2||1|9|");
            wl(w, "|0140|1|" + nomeEmpresa + "|" + cnpj + "|" + uf + "|" + ie + "|" + codMun + "|||");

            // 0150 - Participantes
            Set<String> partKeys = new LinkedHashSet<>();
            for (NfeRegistro nfe : nfes) {
                if ("0".equals(nfe.entradaSaida()) && nfe.nfeChaveAcesso() != null && nfe.nfeChaveAcesso().length() >= 25) {
                    String key = "F" + String.format("%06d", nfe.id());
                    if (!partKeys.contains(key)) {
                        partKeys.add(key);
                        String cnpjForn = nfe.nfeChaveAcesso().substring(6, 20);
                        wl(w, "|0150|" + key + "||01058|" + cnpjForn + "|||" + codMun + "||||||");
                    }
                }
            }

            // 0190 - Unidades
            wl(w, "|0190|CX|Caixa|");
            wl(w, "|0190|FD|Fardo|");
            wl(w, "|0190|KG|Quilograma|");
            wl(w, "|0190|PT|Pote|");
            wl(w, "|0190|UN|Unidade|");

            // 0200 - Produtos
            Set<Long> prodIds = new HashSet<>();
            for (CompraRegistro c : compras) {
                if (!prodIds.contains(c.idProduto())) {
                    prodIds.add(c.idProduto());
                    wl(w, "|0200|" + String.format("%06d", c.idProduto()) + "|" + safe(c.descricao()) + "|||" + safe(c.unidade()) + "|00|" + safe(c.ncm()) + "||" + ncmCap(c.ncm()) + "||0,00|");
                }
            }

            // 0500 - Plano de contas
            wl(w, "|0500|01012016|09|S|1|4.1.1.01.0001|VENDA DE MERCADORIAS|||");
            wl(w, "|0500|01012016|01|S|1|1.1.3.01.0001|MERCADORIA PARA REVENDA|||");

            wl(w, "|0990|" + countBlock("0") + "|");

            // ===== BLOCO A (vazio) =====
            wl(w, "|A001|1|");
            wl(w, "|A990|2|");

            // ===== BLOCO C =====
            wl(w, "|C001|0|");
            wl(w, "|C010|" + cnpj + "|2|");

            // C100 + C170 - NFe entrada (compras)
            for (NfeRegistro nfe : nfes) {
                if ("0".equals(nfe.entradaSaida())) {
                    String key = "F" + String.format("%06d", nfe.id());
                    wl(w, "|C100|0|1|" + key + "|55|00|" + safe(nfe.nfeSerie()) + "|" + String.format("%09d", nfe.nfeNumero())
                        + "|" + safe(nfe.nfeChaveAcesso()) + "|" + fmt(nfe.nfeDataEmissao()) + "|" + fmt(nfe.nfeDataEmissao())
                        + "|" + bd(nfe.totalProdutos()) + "|0|" + bd(nfe.desconto()) + "|0,00|" + bd(nfe.valorFinal())
                        + "|0|0,00|0,00|0,00|" + bd(nfe.valorBaseIcms()) + "|" + bd(nfe.valorIcms())
                        + "|0,00|0,00|0,00|"
                        + bd(nfe.valorPis()) + "|" + bd(nfe.valorCofins()) + "|0,00|0,00|");

                    for (CompraRegistro c : compras) {
                        if (c.idNfe() == nfe.id()) {
                            wl(w, "|C170|" + String.format("%03d", c.item()) + "|" + String.format("%06d", c.idProduto())
                                + "|" + safe(c.descricao()) + "|" + bd5(c.quantidade()) + "|" + safe(c.unidade())
                                + "|" + bd(c.totalItem()) + "|" + bd(c.desconto())
                                + "|0|" + safe(c.icmsCst()) + "|" + safe(c.cfop()) + "||0,00|0,00|0,00|0,00|0,00|0,00"
                                + "|||||||50|" + bd(c.totalItem()) + "|0,6500|||" + bd(c.pisValor())
                                + "|50|" + bd(c.totalItem()) + "|3,0000|||" + bd(c.cofinsValor()) + "|1.1.3.01.0001|");
                        }
                    }
                }
            }

            // C175 - NFCe vendas (modelo 65) - consolidado por CFOP
            Map<String, BigDecimal[]> nfceByCfop = new LinkedHashMap<>();
            for (NfceRegistro n : nfces) {
                if (!"S".equals(n.cupomCancelado())) {
                    String cfop = n.cfop() != null ? n.cfop() : "5102";
                    BigDecimal[] vals = nfceByCfop.computeIfAbsent(cfop, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                    vals[0] = vals[0].add(n.valorFinal()); // total
                    vals[1] = vals[1].add(n.desconto()); // desconto
                    vals[2] = vals[2].add(n.pis()); // pis
                    vals[3] = vals[3].add(n.cofins()); // cofins
                    totalPisVendas = totalPisVendas.add(n.pis());
                    totalCofinsVendas = totalCofinsVendas.add(n.cofins());
                    totalBaseVendas = totalBaseVendas.add(n.valorFinal());
                }
            }

            for (var entry : nfceByCfop.entrySet()) {
                String cfop = entry.getKey();
                BigDecimal[] v = entry.getValue();
                // CST PIS/COFINS: 01=tributavel, 06=aliquota zero
                String cstPisCof = cfop.contains("5405") ? "06" : "01";
                BigDecimal basePis = v[0];
                BigDecimal pisTaxa = new BigDecimal("0.65");
                BigDecimal pisVal = v[2];
                BigDecimal cofinsTaxa = new BigDecimal("3.00");
                BigDecimal cofinsVal = v[3];

                wl(w, "|C175|" + cfop + "|" + bd(v[0]) + "|" + bd(v[1])
                    + "|" + cstPisCof + "|" + bd(basePis) + "|0,65|||" + bd(pisVal)
                    + "|" + cstPisCof + "|" + bd(basePis) + "|3,00|||" + bd(cofinsVal)
                    + "|4.1.1.01.0001||");
            }

            // NFe saidas
            for (NfeRegistro nfe : nfes) {
                if (!"0".equals(nfe.entradaSaida()) && !"S".equals(nfe.cancelado())) {
                    totalPisVendas = totalPisVendas.add(nfe.valorPis());
                    totalCofinsVendas = totalCofinsVendas.add(nfe.valorCofins());
                    totalBaseVendas = totalBaseVendas.add(nfe.valorFinal());
                }
            }

            wl(w, "|C990|" + countBlock("C") + "|");

            // ===== BLOCO D (vazio) =====
            wl(w, "|D001|1|");
            wl(w, "|D990|2|");

            // ===== BLOCO F (vazio) =====
            wl(w, "|F001|1|");
            wl(w, "|F990|2|");

            // ===== BLOCO M - APURACAO PIS/COFINS =====
            wl(w, "|M001|0|");

            // M200 - Consolidacao PIS
            wl(w, "|M200|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|");

            // M400 - Receitas isentas/nao alcancadas PIS
            wl(w, "|M400|01|" + bd(totalBaseVendas) + "|4.1.1.01.0001||");
            wl(w, "|M410|000|" + bd(totalBaseVendas) + "|4.1.1.01.0001||");

            // M600 - Consolidacao COFINS
            wl(w, "|M600|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|");

            // M800 - Receitas isentas/nao alcancadas COFINS
            wl(w, "|M800|01|" + bd(totalBaseVendas) + "|4.1.1.01.0001||");
            wl(w, "|M810|000|" + bd(totalBaseVendas) + "|4.1.1.01.0001||");

            wl(w, "|M990|9|");

            // ===== BLOCO P (vazio) =====
            wl(w, "|P990|1|");

            // ===== BLOCO 1 (vazio) =====
            wl(w, "|1001|1|");
            wl(w, "|1990|2|");

            // ===== BLOCO 9 =====
            wl(w, "|9001|0|");
            for (var entry : regCount.entrySet()) {
                wl(w, "|9900|" + entry.getKey() + "|" + entry.getValue() + "|");
            }
            wl(w, "|9900|9900|" + (regCount.size() + 2) + "|");
            wl(w, "|9900|9990|1|");
            wl(w, "|9900|9999|1|");
            wl(w, "|9990|" + (regCount.size() + 5) + "|");
            wl(w, "|9999|" + (lineCount.get() + 1) + "|");
        }

        log.info("SPED Contribuicoes (v{}) gerado: {} ({} linhas)", versaoLeiaute, file, lineCount.get());
    }

    private void wl(BufferedWriter w, String line) throws Exception {
        w.write(line);
        w.newLine();
        lineCount.incrementAndGet();
        if (line.startsWith("|") && line.length() > 5) {
            String reg = line.substring(1, 5);
            regCount.merge(reg, 1, Integer::sum);
        }
    }

    private int countBlock(String prefix) {
        int count = 0;
        for (var entry : regCount.entrySet()) {
            if (entry.getKey().startsWith(prefix)) count += entry.getValue();
        }
        return count + 1;
    }

    private String fmt(LocalDate d) { return d != null ? d.format(FMT) : ""; }
    private String bd(BigDecimal v) { return v != null ? v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace(".", ",") : "0,00"; }
    private String bd5(BigDecimal v) { return v != null ? v.setScale(5, RoundingMode.HALF_UP).toPlainString().replace(".", ",") : "0,00000"; }
    private String safe(String s) { return s != null ? s.trim() : ""; }
    private String ncmCap(String ncm) { return (ncm != null && ncm.length() >= 2) ? ncm.substring(0, 2) : ""; }
}
