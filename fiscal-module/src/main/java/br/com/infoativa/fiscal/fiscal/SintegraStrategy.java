package br.com.infoativa.fiscal.fiscal;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.NfceRepository;
import br.com.infoativa.fiscal.repository.NfeRepository;
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

public class SintegraStrategy implements FiscalObligationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SintegraStrategy.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicInteger lineCount = new AtomicInteger(0);

    @Override
    public String name() { return "SINTEGRA"; }

    @Override
    public void generate(Connection conn, Periodo periodo, Path outputDir) throws Exception {
        Path file = outputDir.resolve("TXT").resolve("SINTEGRA_" + periodo.mesAnoRef() + ".txt");
        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);

        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            // Registro 10 - Mestre do Estabelecimento
            writeLine(w, String.format("10%-14s%-14s%-35s%-30s%-5s%8s%8s%-2s%-5s%-3s%-10s",
                "", "", "EMPRESA", "ENDERECO", "NUM", fmt(periodo.inicio()), fmt(periodo.fim()),
                "BA", "00000", "000", "0000000000"));

            // Registro 11 - Dados complementares
            writeLine(w, String.format("11%-30s%-5s%-10s%-15s%-15s%-14s%-4s",
                "ENDERECO", "NUM", "COMPLEMENTO", "BAIRRO", "CEP", "CONTATO", "FONE"));

            // Registro 50 - Notas Fiscais (NFe saida)
            for (NfeRegistro nfe : nfes) {
                if (!"S".equals(nfe.cancelado())) {
                    String tp = "1".equals(nfe.entradaSaida()) ? "S" : "E";
                    writeLine(w, String.format("50%-14s%2s%8s%-2s55%-6d%3s%-4s%14s%14s%14s%3s",
                        "", tp, fmtDate(nfe.nfeDataEmissao()), "BA",
                        nfe.nfeNumero(), nfe.nfeSerie() != null ? nfe.nfeSerie() : "001",
                        safe(nfe.cfop()),
                        fmtVal(nfe.valorFinal()), fmtVal(nfe.valorBaseIcms()),
                        fmtVal(nfe.valorIcms()), "N"));
                }
            }

            // Registro 60M - Resumo mensal NFCe
            if (!nfces.isEmpty()) {
                BigDecimal totalNfce = BigDecimal.ZERO;
                for (NfceRegistro nfce : nfces) {
                    if (!"S".equals(nfce.cupomCancelado())) {
                        totalNfce = totalNfce.add(nfce.valorFinal());
                    }
                }
                writeLine(w, String.format("60M%-14s%8s65%-6d%-6d%14s",
                    "", fmt(periodo.fim()),
                    nfces.get(0).nfceNumero(),
                    nfces.get(nfces.size()-1).nfceNumero(),
                    fmtVal(totalNfce)));
            }

            // Registro 90 - Totalizacao
            int total50 = (int) nfes.stream().filter(n -> !"S".equals(n.cancelado())).count();
            writeLine(w, String.format("90%-14s%-2s%8d%8d",
                "", "BA", total50, lineCount.get() + 1));

            writeLine(w, "99" + String.format("%08d", lineCount.get() + 1));
        }

        log.info("SINTEGRA gerado: {} ({} linhas)", file, lineCount.get());
    }

    private void writeLine(BufferedWriter w, String line) throws Exception {
        w.write(line);
        w.newLine();
        lineCount.incrementAndGet();
    }

    private String fmt(LocalDate d) { return d != null ? d.format(FMT) : "00000000"; }
    private String fmtDate(LocalDate d) { return d != null ? d.format(FMT) : "00000000"; }
    private String fmtVal(BigDecimal v) {
        if (v == null) return "00000000000000";
        long cents = v.movePointRight(2).longValue();
        return String.format("%014d", cents);
    }
    private String safe(String s) { return s != null ? s : ""; }
}
