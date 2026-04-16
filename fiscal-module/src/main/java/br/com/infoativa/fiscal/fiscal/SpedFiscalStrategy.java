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
 * SPED Fiscal (EFD ICMS/IPI) - Leiaute 019
 * Baseado no arquivo real do cliente.
 * Registros: 0000,0001,0005,0100,0150,0190,0200,0400,0990,
 *            C001,C100,C170,C190,C191,C400,C405,C420,C425,C490,C990,
 *            D001,D990, E001,E100,E110,E990, G001,G990, H001,H990, K001,K990,
 *            B001,B990, 1001,1010,1601,1990, 9001,9900,9990,9999
 */
public class SpedFiscalStrategy implements FiscalObligationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SpedFiscalStrategy.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private final AtomicInteger lineCount = new AtomicInteger(0);
    private final Map<String, Integer> regCount = new LinkedHashMap<>();

    @Override
    public String name() { return "SPED_FISCAL"; }

    @Override
    public void generate(Connection conn, Periodo periodo, Path outputDir) throws Exception {
        Path file = outputDir.resolve("TXT").resolve("SPED_FISCAL_" + periodo.mesAnoRef() + ".txt");
        lineCount.set(0);
        regCount.clear();

        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);
        CompraRepository compraRepo = new CompraRepository(conn);

        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);
        List<CompraRegistro> compras = compraRepo.findByPeriodo(periodo);

        // Company info from PARAMETROS
        String nomeEmpresa = "";
        String cnpj = "";
        String uf = "BA";
        String ie = "";
        String codMun = "";
        try {
            ParametrosRepository paramRepo = new ParametrosRepository(conn);
            ParametrosRegistro params = paramRepo.findFirst();
            if (params != null) {
                uf = params.nfeWebserviceUf().isEmpty() ? "BA" : params.nfeWebserviceUf();
            }
            nomeEmpresa = paramRepo.findNomeEmpresa();
            // Get CNPJ from first NFe chave
            if (!nfes.isEmpty()) {
                String chave = nfes.get(0).nfeChaveAcesso();
                if (chave != null && chave.length() >= 25) {
                    cnpj = chave.substring(6, 20);
                }
            } else if (!nfces.isEmpty()) {
                String chave = nfces.get(0).nfceChaveAcesso();
                if (chave != null && chave.length() >= 25) {
                    cnpj = chave.substring(6, 20);
                }
            }
        } catch (Exception ignored) {}

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            // ===== BLOCO 0 =====
            wl(w, "|0000|019|0|" + fmt(periodo.inicio()) + "|" + fmt(periodo.fim()) + "|" + nomeEmpresa + "|" + cnpj + "||" + uf + "|" + ie + "|" + codMun + "|||B|1|");
            wl(w, "|0001|0|");
            wl(w, "|0005||||||||" + "||");
            wl(w, "|0100|||||00000000||||||||0000000|");

            // 0150 - Participantes (fornecedores from compras)
            Set<String> participantes = new LinkedHashSet<>();
            for (NfeRegistro nfe : nfes) {
                if ("0".equals(nfe.entradaSaida()) && nfe.nfeChaveAcesso() != null) {
                    String cnpjForn = "";
                    if (nfe.nfeChaveAcesso().length() >= 25) cnpjForn = nfe.nfeChaveAcesso().substring(6, 20);
                    String key = "F" + String.format("%06d", nfe.id());
                    if (!participantes.contains(key) && !cnpjForn.isEmpty()) {
                        participantes.add(key);
                        wl(w, "|0150|" + key + "||01058|" + cnpjForn + "|||" + codMun + "||||||");
                    }
                }
            }

            // 0190 - Unidades de medida
            wl(w, "|0190|CX|Caixa|");
            wl(w, "|0190|FD|Fardo|");
            wl(w, "|0190|KG|Quilograma|");
            wl(w, "|0190|PT|Pote|");
            wl(w, "|0190|UN|Unidade|");

            // 0200 - Produtos (from itens compra)
            Set<Long> produtosCadastrados = new HashSet<>();
            for (CompraRegistro c : compras) {
                if (!produtosCadastrados.contains(c.idProduto())) {
                    produtosCadastrados.add(c.idProduto());
                    wl(w, "|0200|" + String.format("%06d", c.idProduto()) + "|" + safe(c.descricao()) + "|" + safe(c.ncm()) + "||" + safe(c.unidade()) + "|" + safe(c.origem()) + "|" + safe(c.ncm()) + "||" + ncmCap(c.ncm()) + "||0,00||");
                }
            }

            // 0400 - Natureza da operacao
            wl(w, "|0400|5102|Venda de mercadoria adquirida ou recebida de terceiros|");
            wl(w, "|0400|5405|Venda de mercadoria adquirida ou recebida de terceiros ST|");

            int bloco0Lines = lineCount.get();
            wl(w, "|0990|" + (bloco0Lines + 1) + "|");

            // ===== BLOCO B (vazio) =====
            wl(w, "|B001|1|");
            wl(w, "|B990|2|");

            // ===== BLOCO C - DOCUMENTOS FISCAIS =====
            wl(w, "|C001|0|");

            BigDecimal totalIcms = BigDecimal.ZERO;
            BigDecimal totalIcmsSt = BigDecimal.ZERO;

            // C100 - NFe entradas (compras)
            for (NfeRegistro nfe : nfes) {
                if ("0".equals(nfe.entradaSaida())) { // Entrada
                    String key = "F" + String.format("%06d", nfe.id());
                    wl(w, "|C100|0|1|" + key + "|55|00|" + safe(nfe.nfeSerie()) + "|" + String.format("%09d", nfe.nfeNumero())
                        + "|" + safe(nfe.nfeChaveAcesso()) + "|" + fmt(nfe.nfeDataEmissao()) + "|" + fmt(nfe.nfeDataEmissao())
                        + "|" + bd(nfe.totalProdutos()) + "|" + (nfe.desconto().compareTo(BigDecimal.ZERO) > 0 ? "1" : "0")
                        + "|" + bd(nfe.desconto()) + "|0,00|" + bd(nfe.valorFinal())
                        + "|0|0,00|0,00|0,00|" + bd(nfe.valorBaseIcms()) + "|" + bd(nfe.valorIcms())
                        + "|0,00|0,00|" + bd(nfe.valorIpi()) + "|0,00|" + bd(nfe.valorPis())
                        + "|" + bd(nfe.valorCofins()) + "|0,00|||||");

                    // C170 - Itens (from NOTA_COMPRA_DETALHE)
                    for (CompraRegistro comp : compras) {
                        if (comp.idNfe() == nfe.id()) {
                            wl(w, "|C170|" + String.format("%03d", comp.item()) + "|" + String.format("%06d", comp.idProduto())
                                + "|" + safe(comp.descricao()) + "|" + bd5(comp.quantidade()) + "|" + safe(comp.unidade())
                                + "|" + bd(comp.totalItem()) + "|" + bd(comp.desconto())
                                + "|0|" + safe(comp.icmsCst()) + "|" + safe(comp.cfop()) + "|" + safe(comp.cfop())
                                + "|" + bd(comp.icmsBc()) + "|" + bd(comp.icmsTaxa()) + "|" + bd(comp.icmsValor())
                                + "|0,00|0,00|0,00||" + safe(comp.origem()) + "||0,00|0,00|0,00|||||||||||||||||");
                        }
                    }

                    // C190 - Analitico
                    wl(w, "|C190|" + safe(nfe.cfop()) + "|" + safe(nfe.cfop()) + "|0,00|" + bd(nfe.valorFinal())
                        + "|" + bd(nfe.valorBaseIcms()) + "|" + bd(nfe.valorIcms())
                        + "|0,00|0,00|" + bd(nfe.valorFinal()) + "|0,00||");

                    totalIcms = totalIcms.add(nfe.valorIcms());
                    totalIcmsSt = totalIcmsSt.add(nfe.valorIcmsSt());
                }
            }

            // C100 - NFe saidas
            for (NfeRegistro nfe : nfes) {
                if (!"0".equals(nfe.entradaSaida()) && !"S".equals(nfe.cancelado())) {
                    wl(w, "|C100|1|0||55|00|" + safe(nfe.nfeSerie()) + "|" + String.format("%09d", nfe.nfeNumero())
                        + "|" + safe(nfe.nfeChaveAcesso()) + "|" + fmt(nfe.nfeDataEmissao()) + "|" + fmt(nfe.nfeDataEmissao())
                        + "|" + bd(nfe.totalProdutos()) + "|0|" + bd(nfe.desconto()) + "|0,00|" + bd(nfe.valorFinal())
                        + "|0|0,00|0,00|0,00|" + bd(nfe.valorBaseIcms()) + "|" + bd(nfe.valorIcms())
                        + "|" + bd(nfe.valorBaseIcmsSt()) + "|" + bd(nfe.valorIcmsSt())
                        + "|" + bd(nfe.valorIpi()) + "|" + bd(nfe.valorFrete())
                        + "|" + bd(nfe.valorPis()) + "|" + bd(nfe.valorCofins()) + "|0,00|||||");
                    wl(w, "|C190|000|" + safe(nfe.cfop()) + "|0,00|" + bd(nfe.valorFinal())
                        + "|" + bd(nfe.valorBaseIcms()) + "|" + bd(nfe.valorIcms())
                        + "|0,00|0,00|" + bd(nfe.valorFinal()) + "|0,00||");
                    totalIcms = totalIcms.add(nfe.valorIcms());
                }
            }

            // NFCe - Resumo diario (C400/C405/C420/C425/C490)
            if (!nfces.isEmpty()) {
                // Group NFCe by date
                Map<LocalDate, List<NfceRegistro>> byDate = new LinkedHashMap<>();
                for (NfceRegistro n : nfces) {
                    if (n.nfceDataEmissao() != null && !"S".equals(n.cupomCancelado())) {
                        byDate.computeIfAbsent(n.nfceDataEmissao(), k -> new ArrayList<>()).add(n);
                    }
                }

                for (var entry : byDate.entrySet()) {
                    LocalDate data = entry.getKey();
                    List<NfceRegistro> dayNfces = entry.getValue();
                    int primeiro = dayNfces.stream().mapToInt(NfceRegistro::nfceNumero).min().orElse(0);
                    int ultimo = dayNfces.stream().mapToInt(NfceRegistro::nfceNumero).max().orElse(0);
                    BigDecimal totalDia = dayNfces.stream().map(NfceRegistro::valorFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalCanc = BigDecimal.ZERO;

                    wl(w, "|C405|" + fmt(data) + "|" + primeiro + "|" + ultimo + "|" + bd(totalDia) + "|0,00|" + bd(totalCanc) + "|");

                    // C420 - Totalizador resumo por CST
                    Map<String, BigDecimal> byCst = new LinkedHashMap<>();
                    for (NfceRegistro n : dayNfces) {
                        String cst = n.cfop() != null ? n.cfop() : "F";
                        byCst.merge(cst, n.valorFinal(), BigDecimal::add);
                    }
                    for (var cstEntry : byCst.entrySet()) {
                        wl(w, "|C420|" + cstEntry.getKey() + "|" + bd(cstEntry.getValue()) + "|" + cstEntry.getKey() + "|");
                    }

                    // C490 - Analitico por CFOP
                    Map<String, BigDecimal[]> byCfop = new LinkedHashMap<>();
                    for (NfceRegistro n : dayNfces) {
                        String cfop = n.cfop() != null ? n.cfop() : "5102";
                        BigDecimal[] vals = byCfop.computeIfAbsent(cfop, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                        vals[0] = vals[0].add(n.valorFinal());
                        vals[1] = vals[1].add(n.baseIcms());
                        vals[2] = vals[2].add(n.icms());
                    }
                    for (var cfopEntry : byCfop.entrySet()) {
                        BigDecimal[] v = cfopEntry.getValue();
                        wl(w, "|C490|000|" + cfopEntry.getKey() + "|0,00|" + bd(v[0]) + "|" + bd(v[1]) + "|" + bd(v[2]) + "|0,00|0,00|" + bd(v[0]) + "|");
                    }

                    for (NfceRegistro n : dayNfces) {
                        totalIcms = totalIcms.add(n.icms());
                    }
                }
            }

            wl(w, "|C990|" + countSinceReg("C001") + "|");

            // ===== BLOCO D (vazio) =====
            wl(w, "|D001|1|");
            wl(w, "|D990|2|");

            // ===== BLOCO E - APURACAO ICMS =====
            wl(w, "|E001|0|");
            wl(w, "|E100|" + fmt(periodo.inicio()) + "|" + fmt(periodo.fim()) + "|");
            wl(w, "|E110|" + bd(totalIcms) + "|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|" + bd(totalIcms) + "|0,00|" + bd(totalIcms) + "|0,00|0,00|");
            wl(w, "|E990|4|");

            // ===== BLOCO G (vazio) =====
            wl(w, "|G001|1|");
            wl(w, "|G990|2|");

            // ===== BLOCO H (vazio) =====
            wl(w, "|H001|1|");
            wl(w, "|H990|2|");

            // ===== BLOCO K (vazio) =====
            wl(w, "|K001|1|");
            wl(w, "|K990|2|");

            // ===== BLOCO 1 =====
            wl(w, "|1001|0|");
            wl(w, "|1010|N|N|N|N|N|N|N|N|N|N|N|N|");
            wl(w, "|1601|0,00|0,00|0,00||");
            wl(w, "|1990|4|");

            // ===== BLOCO 9 =====
            wl(w, "|9001|0|");
            // Count registers for 9900
            for (var entry : regCount.entrySet()) {
                wl(w, "|9900|" + entry.getKey() + "|" + entry.getValue() + "|");
            }
            wl(w, "|9900|9900|" + (regCount.size() + 2) + "|");
            wl(w, "|9900|9990|1|");
            wl(w, "|9900|9999|1|");
            wl(w, "|9990|" + (regCount.size() + 5) + "|");
            wl(w, "|9999|" + (lineCount.get() + 1) + "|");
        }

        log.info("SPED Fiscal gerado: {} ({} linhas)", file, lineCount.get());
    }

    private void wl(BufferedWriter w, String line) throws Exception {
        w.write(line);
        w.newLine();
        lineCount.incrementAndGet();
        // Track register type
        if (line.startsWith("|") && line.length() > 5) {
            String reg = line.substring(1, 5);
            regCount.merge(reg, 1, Integer::sum);
        }
    }

    private int countSinceReg(String startReg) {
        int count = 0;
        boolean counting = false;
        for (var entry : regCount.entrySet()) {
            if (entry.getKey().equals(startReg)) counting = true;
            if (counting) count += entry.getValue();
        }
        return count + 1; // +1 for the closing register itself
    }

    private String fmt(LocalDate d) { return d != null ? d.format(FMT) : ""; }
    private String bd(BigDecimal v) { return v != null ? v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace(".", ",") : "0,00"; }
    private String bd5(BigDecimal v) { return v != null ? v.setScale(5, RoundingMode.HALF_UP).toPlainString().replace(".", ",") : "0,00000"; }
    private String safe(String s) { return s != null ? s.trim() : ""; }
    private String ncmCap(String ncm) {
        if (ncm == null || ncm.length() < 2) return "";
        return ncm.substring(0, 2);
    }
}
