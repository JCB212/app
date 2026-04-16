package br.com.infoativa.fiscal.fiscal;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.NfceRepository;
import br.com.infoativa.fiscal.repository.NfeRepository;
import br.com.infoativa.fiscal.repository.CompraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SpedContribuicoesStrategy implements FiscalObligationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SpedContribuicoesStrategy.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private final AtomicInteger lineCount = new AtomicInteger(0);

    @Override
    public String name() { return "SPED_CONTRIBUICOES"; }

    @Override
    public void generate(Connection conn, Periodo periodo, Path outputDir) throws Exception {
        Path file = outputDir.resolve("TXT").resolve("SPED_CONTRIBUICOES_" + periodo.mesAnoRef() + ".txt");
        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);
        CompraRepository compraRepo = new CompraRepository(conn);

        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);
        List<CompraRegistro> compras = compraRepo.findByPeriodo(periodo);

        BigDecimal totalPis = BigDecimal.ZERO;
        BigDecimal totalCofins = BigDecimal.ZERO;
        BigDecimal totalBaseCalcPis = BigDecimal.ZERO;
        BigDecimal totalBaseCalcCofins = BigDecimal.ZERO;

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            // Bloco 0 - Abertura
            writeLine(w, "|0000|006|0|" + fmt(periodo.inicio()) + "|" + fmt(periodo.fim()) + "|||||1||");
            writeLine(w, "|0001|0|");
            writeLine(w, "|0100||||||||||||||");
            writeLine(w, "|0110|1|1|1|||");
            writeLine(w, "|0990|4|");

            // Bloco A - Servicos (vazio)
            writeLine(w, "|A001|1|");
            writeLine(w, "|A990|2|");

            // Bloco C - Documentos Fiscais
            writeLine(w, "|C001|0|");

            // NFe saidas
            for (NfeRegistro nfe : nfes) {
                if (!"S".equals(nfe.cancelado())) {
                    writeLine(w, "|C100|1|0|" + safe(nfe.nfeChaveAcesso()) + "|1|00||55|"
                        + nfe.nfeSerie() + "|" + nfe.nfeNumero() + "|" + fmtDate(nfe.nfeDataEmissao())
                        + "|" + bd(nfe.totalProdutos()) + "|" + bd(nfe.desconto())
                        + "|0,00|" + bd(nfe.valorFinal()) + "|1|0,00|0,00|0,00|0,00|");
                    totalPis = totalPis.add(nfe.valorPis());
                    totalCofins = totalCofins.add(nfe.valorCofins());
                    totalBaseCalcPis = totalBaseCalcPis.add(nfe.valorFinal());
                    totalBaseCalcCofins = totalBaseCalcCofins.add(nfe.valorFinal());
                }
            }

            // NFCe - C380
            for (NfceRegistro nfce : nfces) {
                if (!"S".equals(nfce.cupomCancelado())) {
                    writeLine(w, "|C380|65|" + nfce.nfceSerie() + "|" + nfce.nfceNumero()
                        + "|" + fmtDate(nfce.nfceDataEmissao()) + "|" + bd(nfce.totalProdutos())
                        + "|" + bd(nfce.desconto()) + "|" + bd(nfce.valorFinal()) + "|");
                    totalPis = totalPis.add(nfce.pis());
                    totalCofins = totalCofins.add(nfce.cofins());
                    totalBaseCalcPis = totalBaseCalcPis.add(nfce.valorFinal());
                    totalBaseCalcCofins = totalBaseCalcCofins.add(nfce.valorFinal());
                }
            }

            writeLine(w, "|C990|" + (nfes.size() + nfces.size() + 2) + "|");

            // Bloco D - Servicos de Transporte (vazio)
            writeLine(w, "|D001|1|");
            writeLine(w, "|D990|2|");

            // Bloco F - Outras operacoes (vazio)
            writeLine(w, "|F001|1|");
            writeLine(w, "|F990|2|");

            // Bloco M - Apuracao
            writeLine(w, "|M001|0|");
            writeLine(w, "|M100|||" + bd(totalBaseCalcPis) + "|1,65|" + bd(totalPis) + "||||||||");
            writeLine(w, "|M200|" + bd(totalPis) + "|0,00|0,00|0,00|" + bd(totalPis) + "||0,00|" + bd(totalPis) + "|");
            writeLine(w, "|M500|||" + bd(totalBaseCalcCofins) + "|7,60|" + bd(totalCofins) + "||||||||");
            writeLine(w, "|M600|" + bd(totalCofins) + "|0,00|0,00|0,00|" + bd(totalCofins) + "||0,00|" + bd(totalCofins) + "|");
            writeLine(w, "|M990|7|");

            // Bloco 9
            writeLine(w, "|9001|0|");
            writeLine(w, "|9900|0000|1|");
            writeLine(w, "|9900|9999|1|");
            writeLine(w, "|9990|3|");
            writeLine(w, "|9999|" + lineCount.get() + "|");
        }

        log.info("SPED Contribuicoes gerado: {} ({} linhas)", file, lineCount.get());
    }

    private void writeLine(BufferedWriter w, String line) throws Exception {
        w.write(line);
        w.newLine();
        lineCount.incrementAndGet();
    }

    private String fmt(LocalDate d) { return d != null ? d.format(FMT) : ""; }
    private String fmtDate(LocalDate d) { return d != null ? d.format(FMT) : ""; }
    private String bd(BigDecimal v) { return v != null ? v.setScale(2).toPlainString().replace(".", ",") : "0,00"; }
    private String safe(String s) { return s != null ? s : ""; }
}
