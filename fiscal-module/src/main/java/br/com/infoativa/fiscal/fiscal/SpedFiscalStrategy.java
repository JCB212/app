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

public class SpedFiscalStrategy implements FiscalObligationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SpedFiscalStrategy.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private final AtomicInteger lineCount = new AtomicInteger(0);

    @Override
    public String name() { return "SPED_FISCAL"; }

    @Override
    public void generate(Connection conn, Periodo periodo, Path outputDir) throws Exception {
        Path file = outputDir.resolve("TXT").resolve("SPED_FISCAL_" + periodo.mesAnoRef() + ".txt");
        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);
        CompraRepository compraRepo = new CompraRepository(conn);

        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);
        List<CompraRegistro> compras = compraRepo.findByPeriodo(periodo);

        BigDecimal totalIcms = BigDecimal.ZERO;
        BigDecimal totalIcmsSt = BigDecimal.ZERO;
        BigDecimal totalPis = BigDecimal.ZERO;
        BigDecimal totalCofins = BigDecimal.ZERO;

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            // Bloco 0 - Abertura
            writeLine(w, "|0000|015|0|" + fmt(periodo.inicio()) + "|" + fmt(periodo.fim()) + "|||||||BRL|");
            writeLine(w, "|0001|0|");
            writeLine(w, "|0005||||||||");
            writeLine(w, "|0100||||||||||||||");
            writeLine(w, "|0990|5|");

            // Bloco C - Documentos Fiscais
            writeLine(w, "|C001|0|");

            // NFe (modelo 55) saidas
            for (NfeRegistro nfe : nfes) {
                if ("N".equals(nfe.cancelado()) || nfe.cancelado() == null) {
                    String es = "1".equals(nfe.entradaSaida()) ? "1" : "0";
                    writeLine(w, "|C100|" + es + "|1||55|00|" + nfe.nfeSerie() + "|" + nfe.nfeNumero()
                        + "|" + safe(nfe.nfeChaveAcesso()) + "|" + fmtDate(nfe.nfeDataEmissao())
                        + "|" + fmtDate(nfe.nfeDataEmissao())
                        + "|" + bd(nfe.totalProdutos()) + "|" + bd(nfe.desconto())
                        + "|0,00|" + bd(nfe.valorFrete()) + "|" + bd(nfe.valorSeguro())
                        + "|0,00|0,00|" + bd(nfe.valorFinal())
                        + "|" + bd(nfe.valorBaseIcms()) + "|" + bd(nfe.valorIcms())
                        + "|" + bd(nfe.valorBaseIcmsSt()) + "|" + bd(nfe.valorIcmsSt())
                        + "|" + bd(nfe.valorIpi())
                        + "|" + bd(nfe.valorPis()) + "|" + bd(nfe.valorCofins()) + "|0,00|");
                    totalIcms = totalIcms.add(nfe.valorIcms());
                    totalIcmsSt = totalIcmsSt.add(nfe.valorIcmsSt());
                    totalPis = totalPis.add(nfe.valorPis());
                    totalCofins = totalCofins.add(nfe.valorCofins());
                }
            }

            // NFCe (modelo 65) - usando registro C400/C405/C420
            if (!nfces.isEmpty()) {
                writeLine(w, "|C400|2D|65|001|");
                BigDecimal totalNfce = BigDecimal.ZERO;
                BigDecimal totalCancNfce = BigDecimal.ZERO;
                for (NfceRegistro nfce : nfces) {
                    if ("N".equals(nfce.cupomCancelado()) || nfce.cupomCancelado() == null) {
                        totalNfce = totalNfce.add(nfce.valorFinal());
                        totalIcms = totalIcms.add(nfce.icms());
                        totalPis = totalPis.add(nfce.pis());
                        totalCofins = totalCofins.add(nfce.cofins());
                    } else {
                        totalCancNfce = totalCancNfce.add(nfce.valorFinal());
                    }
                }
                writeLine(w, "|C405|" + fmt(periodo.fim()) + "|1|"
                    + nfces.get(nfces.size()-1).nfceNumero() + "|" + bd(totalNfce)
                    + "|0,00|" + bd(totalCancNfce) + "|");
                writeLine(w, "|C420|F|" + bd(totalNfce) + "|0060|");
                writeLine(w, "|C490|" + nfces.get(0).cfop() + "|" + bd(totalNfce) + "|||||||");
            }

            int blocoC = lineCount.get() - 5; // approximate
            writeLine(w, "|C990|" + (blocoC + 2) + "|");

            // Bloco E - Apuracao ICMS
            writeLine(w, "|E001|0|");
            writeLine(w, "|E100|" + fmt(periodo.inicio()) + "|" + fmt(periodo.fim()) + "|");
            writeLine(w, "|E110|" + bd(totalIcms) + "|0,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|"
                + bd(totalIcms) + "|0,00|" + bd(totalIcms) + "|");
            writeLine(w, "|E990|4|");

            // Bloco H - Inventario (vazio)
            writeLine(w, "|H001|1|");
            writeLine(w, "|H990|2|");

            // Bloco 9 - Encerramento
            writeLine(w, "|9001|0|");
            writeLine(w, "|9900|0000|1|");
            writeLine(w, "|9900|C100|" + nfes.size() + "|");
            writeLine(w, "|9900|9999|1|");
            writeLine(w, "|9990|4|");
            writeLine(w, "|9999|" + lineCount.get() + "|");
        }

        log.info("SPED Fiscal gerado: {} ({} linhas)", file, lineCount.get());
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
