package br.com.infoativa.fiscal.report;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gera PDFs completos com TODOS os itens discriminados, multipaginas.
 */
public class PdfReportService {

    private static final Logger log = LoggerFactory.getLogger(PdfReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float M = 35; // margin
    private static final float LH = 11; // line height

    // ===== RESUMO DE VENDAS =====
    public void gerarResumoVendas(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);
        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);

        var nfcesOk = nfces.stream().filter(n -> !"S".equals(n.cupomCancelado())).toList();
        var nfesOk = nfes.stream().filter(n -> !"S".equals(n.cancelado())).toList();
        BigDecimal totNfce = nfcesOk.stream().map(NfceRegistro::valorFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totNfe = nfesOk.stream().map(NfeRegistro::valorFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int cancelados = nfces.size() - nfcesOk.size() + nfes.size() - nfesOk.size();

        Path pdf = outputDir.resolve("PDF").resolve("Resumo_Vendas_" + periodo.mesAnoRef() + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PdfWriter pw = new PdfWriter(doc);
            pw.title("RESUMO DE VENDAS");
            pw.sub("Periodo: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT));
            pw.sub("Gerado em: " + LocalDate.now().format(FMT));
            pw.line();

            pw.section("RESUMO GERAL");
            pw.text("NFCe emitidas: " + nfcesOk.size() + "  |  NFe emitidas: " + nfesOk.size() + "  |  Cancelados: " + cancelados);
            pw.text("Total NFCe: " + m(totNfce) + "  |  Total NFe: " + m(totNfe));
            pw.bold("TOTAL GERAL: " + m(totNfce.add(totNfe)));
            pw.gap();

            // NFCe detalhado
            pw.section("VENDAS NFCe - DETALHADO (" + nfcesOk.size() + " cupons)");
            pw.header7("Numero    Data          CFOP    Produtos      Desconto      ICMS          PIS        COFINS      Total");
            for (NfceRegistro n : nfcesOk) {
                pw.row7(String.format("%-9d %-13s %-7s %13s %13s %13s %10s %10s %13s",
                    n.nfceNumero(), fd(n.nfceDataEmissao()), s(n.cfop(),7),
                    m(n.totalProdutos()), m(n.desconto()), m(n.icms()),
                    m(n.pis()), m(n.cofins()), m(n.valorFinal())));
            }
            pw.gap();

            // NFe detalhado
            pw.section("VENDAS NFe - DETALHADO (" + nfesOk.size() + " notas)");
            pw.header7("Numero    Data          CFOP    Produtos      Desconto      ICMS       ICMS ST      IPI          Total");
            for (NfeRegistro n : nfesOk) {
                pw.row7(String.format("%-9d %-13s %-7s %13s %13s %13s %10s %10s %13s",
                    n.nfeNumero(), fd(n.nfeDataEmissao()), s(n.cfop(),7),
                    m(n.totalProdutos()), m(n.desconto()), m(n.valorIcms()),
                    m(n.valorIcmsSt()), m(n.valorIpi()), m(n.valorFinal())));
            }

            pw.finish();
            doc.save(pdf.toFile());
        }
        log.info("PDF Vendas: {} ({} paginas)", pdf, "multi");
    }

    // ===== RESUMO DE IMPOSTOS =====
    public void gerarResumoImpostos(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);
        var nfces = nfceRepo.findByPeriodo(periodo).stream().filter(n -> !"S".equals(n.cupomCancelado())).toList();
        var nfes = nfeRepo.findByPeriodo(periodo).stream().filter(n -> !"S".equals(n.cancelado())).toList();

        BigDecimal icmsNfce = nfces.stream().map(NfceRegistro::icms).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pisNfce = nfces.stream().map(NfceRegistro::pis).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cofNfce = nfces.stream().map(NfceRegistro::cofins).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal icmsNfe = nfes.stream().map(NfeRegistro::valorIcms).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal icmsStNfe = nfes.stream().map(NfeRegistro::valorIcmsSt).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pisNfe = nfes.stream().map(NfeRegistro::valorPis).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cofNfe = nfes.stream().map(NfeRegistro::valorCofins).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ipiNfe = nfes.stream().map(NfeRegistro::valorIpi).reduce(BigDecimal.ZERO, BigDecimal::add);

        Path pdf = outputDir.resolve("PDF").resolve("Resumo_Impostos_" + periodo.mesAnoRef() + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PdfWriter pw = new PdfWriter(doc);
            pw.title("RESUMO DE IMPOSTOS");
            pw.sub("Periodo: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT));
            pw.line();

            pw.section("IMPOSTOS - NFCe (Consumidor Final)");
            pw.text("ICMS:    " + m(icmsNfce));
            pw.text("PIS:     " + m(pisNfce));
            pw.text("COFINS:  " + m(cofNfce));
            pw.bold("Subtotal NFCe: " + m(icmsNfce.add(pisNfce).add(cofNfce)));
            pw.gap();

            pw.section("IMPOSTOS - NFe");
            pw.text("ICMS:    " + m(icmsNfe));
            pw.text("ICMS ST: " + m(icmsStNfe));
            pw.text("PIS:     " + m(pisNfe));
            pw.text("COFINS:  " + m(cofNfe));
            pw.text("IPI:     " + m(ipiNfe));
            pw.bold("Subtotal NFe: " + m(icmsNfe.add(icmsStNfe).add(pisNfe).add(cofNfe).add(ipiNfe)));
            pw.gap();

            BigDecimal total = icmsNfce.add(pisNfce).add(cofNfce).add(icmsNfe).add(icmsStNfe).add(pisNfe).add(cofNfe).add(ipiNfe);
            pw.section("TOTAL DE IMPOSTOS: " + m(total));
            pw.gap();

            // Discriminado por nota
            pw.section("NFCe - IMPOSTOS POR CUPOM");
            pw.header7("Numero    Data          BaseICMS      ICMS          PIS        COFINS      Total");
            for (var n : nfces) {
                pw.row7(String.format("%-9d %-13s %13s %13s %10s %10s %13s",
                    n.nfceNumero(), fd(n.nfceDataEmissao()), m(n.baseIcms()), m(n.icms()),
                    m(n.pis()), m(n.cofins()), m(n.valorFinal())));
            }
            pw.gap();

            pw.section("NFe - IMPOSTOS POR NOTA");
            pw.header7("Numero    Data          ICMS       ICMS ST      PIS        COFINS      IPI          Total");
            for (var n : nfes) {
                pw.row7(String.format("%-9d %-13s %13s %10s %10s %10s %10s %13s",
                    n.nfeNumero(), fd(n.nfeDataEmissao()), m(n.valorIcms()), m(n.valorIcmsSt()),
                    m(n.valorPis()), m(n.valorCofins()), m(n.valorIpi()), m(n.valorFinal())));
            }

            pw.finish();
            doc.save(pdf.toFile());
        }
        log.info("PDF Impostos gerado: {}", pdf);
    }

    // ===== RESUMO DE COMPRAS =====
    public void gerarResumoCompras(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        CompraRepository repo = new CompraRepository(conn);
        NotaCompraRepository ncRepo = new NotaCompraRepository(conn);
        List<CompraRegistro> itens = repo.findByPeriodo(periodo);
        List<NotaCompraRegistro> notas = ncRepo.findByPeriodo(periodo);

        BigDecimal totItens = itens.stream().map(CompraRegistro::totalItem).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totIcms = itens.stream().map(CompraRegistro::icmsValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totPis = itens.stream().map(CompraRegistro::pisValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totCof = itens.stream().map(CompraRegistro::cofinsValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totIpi = itens.stream().map(CompraRegistro::ipiValor).reduce(BigDecimal.ZERO, BigDecimal::add);

        Path pdf = outputDir.resolve("PDF").resolve("Resumo_Compras_" + periodo.mesAnoRef() + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PdfWriter pw = new PdfWriter(doc);
            pw.title("RESUMO DE COMPRAS (FORNECEDORES)");
            pw.sub("Periodo: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT));
            pw.line();

            pw.section("RESUMO GERAL");
            pw.text("Total de notas: " + notas.size() + "  |  Total de itens: " + itens.size());
            pw.text("Valor total: " + m(totItens));
            pw.text("ICMS: " + m(totIcms) + "  |  PIS: " + m(totPis) + "  |  COFINS: " + m(totCof) + "  |  IPI: " + m(totIpi));
            pw.gap();

            // Notas cabecalho
            pw.section("NOTAS DE COMPRA (" + notas.size() + ")");
            pw.header7("Nota      Data          CFOP    Produtos      ICMS          PIS        COFINS      IPI          Total");
            for (NotaCompraRegistro nc : notas) {
                pw.row7(String.format("%-9s %-13s %-7s %13s %13s %10s %10s %10s %13s",
                    s(nc.nota(),9), fd(nc.dataEmissao()), s(nc.cfop(),7),
                    m(nc.valorProdutos()), m(nc.valorIcms()), m(nc.valorPis()),
                    m(nc.valorCofins()), m(nc.valorIpi()), m(nc.valorTotal())));
            }
            pw.gap();

            // Itens detalhados
            pw.section("ITENS DISCRIMINADOS (" + itens.size() + ")");
            pw.header6("Item   Descricao                            NCM          CFOP    Qtde       Unitario      Total");
            for (CompraRegistro c : itens) {
                pw.row6(String.format("%-6d %-36s %-12s %-7s %10s %13s %13s",
                    c.item(), s(c.descricao(),36), s(c.ncm(),12), s(c.cfop(),7),
                    q(c.quantidade()), m(c.valorUnitario()), m(c.totalItem())));
            }

            pw.finish();
            doc.save(pdf.toFile());
        }
        log.info("PDF Compras gerado: {}", pdf);
    }

    // ===== HELPER: Multi-page PDF Writer =====
    private static class PdfWriter {
        final PDDocument doc;
        PDPageContentStream cs;
        float y;
        float pageW;

        PdfWriter(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        void newPage() throws IOException {
            if (cs != null) cs.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            pageW = page.getMediaBox().getWidth();
            y = page.getMediaBox().getHeight() - M;
        }

        void checkPage() throws IOException {
            if (y < M + 20) newPage();
        }

        void title(String t) throws IOException {
            cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
            w(t, M); y -= 4;
        }

        void sub(String t) throws IOException {
            cs.setFont(PDType1Font.HELVETICA, 10);
            w(t, M);
        }

        void section(String t) throws IOException {
            checkPage();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
            w(t, M); y -= 2;
        }

        void bold(String t) throws IOException {
            checkPage();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
            w(t, M + 15);
        }

        void text(String t) throws IOException {
            checkPage();
            cs.setFont(PDType1Font.HELVETICA, 9);
            w(t, M + 15);
        }

        void header7(String t) throws IOException {
            checkPage();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 6.5f);
            w(t, M + 5); y -= 1;
        }

        void header6(String t) throws IOException {
            checkPage();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 6);
            w(t, M + 5); y -= 1;
        }

        void row7(String t) throws IOException {
            checkPage();
            cs.setFont(PDType1Font.HELVETICA, 6.5f);
            w(t, M + 5);
        }

        void row6(String t) throws IOException {
            checkPage();
            cs.setFont(PDType1Font.HELVETICA, 6);
            w(t, M + 5);
        }

        void gap() throws IOException {
            y -= LH;
        }

        void line() throws IOException {
            cs.moveTo(M, y);
            cs.lineTo(pageW - M, y);
            cs.setStrokingColor(0.7f, 0.7f, 0.7f);
            cs.stroke();
            y -= LH;
        }

        void finish() throws IOException {
            if (cs != null) cs.close();
        }

        private void w(String text, float x) throws IOException {
            cs.beginText();
            cs.newLineAtOffset(x, y);
            cs.showText(text);
            cs.endText();
            y -= LH;
        }
    }

    // ===== Formatters =====
    private String m(BigDecimal v) {
        if (v == null) return "0,00";
        return "R$ " + String.format("%,.2f", v).replace(",", "X").replace(".", ",").replace("X", ".");
    }
    private String q(BigDecimal v) {
        if (v == null) return "0";
        return v.stripTrailingZeros().toPlainString().replace(".", ",");
    }
    private String fd(LocalDate d) { return d != null ? d.format(FMT) : ""; }
    private String s(String v, int max) {
        if (v == null) return "";
        v = v.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
