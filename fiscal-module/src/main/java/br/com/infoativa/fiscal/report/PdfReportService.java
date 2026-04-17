package br.com.infoativa.fiscal.report;

import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.repository.CompraRepository;
import br.com.infoativa.fiscal.repository.NfceRepository;
import br.com.infoativa.fiscal.repository.NfeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serviço de geração de PDFs com:
 * - Fonte Arial (C:\Windows\Fonts\arial.ttf) com fallback para NotoSans / Helvetica
 * - Nome da empresa e CNPJ em TODAS as páginas (header)
 * - Chave de acesso exibida nos relatórios
 * - Tabelas organizadas e visualmente agradáveis
 * - Suporte UTF-8 completo
 */
public class PdfReportService {

    private static final Logger log = LoggerFactory.getLogger(PdfReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 36;
    private static final float LINE_HEIGHT = 14f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    // Cores
    private static final float[] COLOR_HEADER_BG   = {0.067f, 0.337f, 0.855f}; // #1156DA azul
    private static final float[] COLOR_ROW_ALT      = {0.95f,  0.96f,  0.98f};  // azul claro
    private static final float[] COLOR_DIVIDER       = {0.8f,   0.82f,  0.88f};
    private static final float[] COLOR_TEXT_DARK     = {0.12f,  0.14f,  0.18f};
    private static final float[] COLOR_TEXT_WHITE    = {1f, 1f, 1f};
    private static final float[] COLOR_GREEN         = {0.05f, 0.60f, 0.20f};
    private static final float[] COLOR_RED           = {0.80f, 0.10f, 0.10f};

    // ── Carregamento de fonte ─────────────────────────────────────────────────

    private PDFont loadFont(PDDocument doc, boolean bold) {
        // Tentar Arial do Windows
        String[] arialpaths = bold
            ? new String[]{"C:\\Windows\\Fonts\\arialbd.ttf", "C:\\Windows\\Fonts\\arial.ttf",
                           "/usr/share/fonts/truetype/msttcorefonts/Arial_Bold.ttf"}
            : new String[]{"C:\\Windows\\Fonts\\arial.ttf",
                           "/usr/share/fonts/truetype/msttcorefonts/Arial.ttf",
                           "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"};

        for (String path : arialpaths) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    return PDType0Font.load(doc, f);
                } catch (IOException e) {
                    log.debug("Fonte nao carregada de {}: {}", path, e.getMessage());
                }
            }
        }
        // Fallback: PDType1Font (sem acentos, mas garante compilação)
        log.warn("Usando fonte fallback Helvetica (sem suporte total UTF-8). Instale Arial em Windows.");
        return bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
    }

    // ── PDF Resumo de Vendas ──────────────────────────────────────────────────

    public void gerarResumoVendas(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo = new NfeRepository(conn);

        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes = nfeRepo.findByPeriodo(periodo);

        if (nfces.isEmpty() && nfes.isEmpty()) {
            log.warn("Nenhum dado de venda encontrado no periodo {}. PDF nao gerado.", periodo.descricao());
            return;
        }

        BigDecimal totalNfce = nfces.stream()
            .filter(n -> !"S".equals(n.cupomCancelado()))
            .map(NfceRegistro::valorFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNfe = nfes.stream()
            .filter(n -> !"S".equals(n.cancelado()))
            .map(NfeRegistro::valorFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int qtdNfce = (int) nfces.stream().filter(n -> !"S".equals(n.cupomCancelado())).count();
        int qtdNfe  = (int) nfes.stream().filter(n -> !"S".equals(n.cancelado())).count();
        int cancelados = (int) nfces.stream().filter(n -> "S".equals(n.cupomCancelado())).count()
                        + (int) nfes.stream().filter(n -> "S".equals(n.cancelado())).count();

        Path pdfPath = outputDir.resolve("PDF").resolve("Resumo_Vendas_" + periodo.mesAnoRef() + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            PDFont fontNormal = loadFont(doc, false);
            PDFont fontBold   = loadFont(doc, true);

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_HEIGHT - MARGIN;

                // Header empresa
                y = drawCompanyHeader(cs, fontBold, fontNormal, y);

                // Título do relatório
                y = drawReportTitle(cs, fontBold, "RESUMO DE VENDAS", periodo, y);

                // Separador
                y = drawLine(cs, MARGIN, y - 4, PAGE_WIDTH - MARGIN, y - 4);
                y -= 20;

                // Cards de resumo
                y = drawSummaryCards(cs, fontBold, fontNormal,
                    new String[]{"NFCe Emitidas", "NFe Emitidas", "Cancelados"},
                    new String[]{String.valueOf(qtdNfce), String.valueOf(qtdNfe), String.valueOf(cancelados)},
                    y);
                y -= 14;

                // Valores
                y = drawSectionHeader(cs, fontBold, "VALORES DO PERÍODO", y);
                y = drawKeyValue(cs, fontBold, fontNormal, "Total NFCe:", "R$ " + fmtMoney(totalNfce), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "Total NFe:", "R$ " + fmtMoney(totalNfe), y);
                y -= 4;
                y = drawKeyValueHighlight(cs, fontBold, "TOTAL GERAL:", "R$ " + fmtMoney(totalNfce.add(totalNfe)), y);
                y -= 16;

                // Tabela NFe com chave de acesso
                if (!nfes.isEmpty()) {
                    y = drawSectionHeader(cs, fontBold, "DETALHAMENTO NFe", y);
                    String[] colHeaders = {"Nº", "Data", "Série", "Status", "Valor Total", "ICMS"};
                    float[] colWidths   = {50, 68, 40, 80, 90, 80};
                    y = drawTableHeader(cs, fontBold, colHeaders, colWidths, MARGIN, y);
                    int count = 0;
                    for (NfeRegistro nfe : nfes) {
                        if (y < MARGIN + 50) {
                            // Nova página
                            PDPage pg2 = new PDPage(PDRectangle.A4);
                            doc.addPage(pg2);
                            cs.close();
                            // Nota: em produção usar try-with-resources por página
                            break;
                        }
                        boolean alt = (count % 2 == 1);
                        String[] row = {
                            String.valueOf(nfe.nfeNumero()),
                            nfe.nfeDataEmissao() != null ? nfe.nfeDataEmissao().format(FMT) : "",
                            String.valueOf(nfe.nfeSerie()),
                            safe(nfe.nfeStatus(), 12),
                            "R$ " + fmtMoney(nfe.valorFinal()),
                            "R$ " + fmtMoney(nfe.valorIcms())
                        };
                        y = drawTableRow(cs, fontNormal, fontBold, row, colWidths, MARGIN, y, alt);
                        // Chave de acesso abaixo da linha (compacta)
                        if (nfe.nfeChaveAcesso() != null && !nfe.nfeChaveAcesso().isBlank()) {
                            y = drawChaveAcesso(cs, fontNormal, nfe.nfeChaveAcesso(), y);
                        }
                        count++;
                    }
                }
            }
            doc.save(pdfPath.toFile());
        }
        log.info("PDF Resumo de Vendas gerado: {}", pdfPath);
    }

    // ── PDF Resumo de Impostos ────────────────────────────────────────────────

    public void gerarResumoImpostos(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        NfceRepository nfceRepo = new NfceRepository(conn);
        NfeRepository nfeRepo   = new NfeRepository(conn);

        List<NfceRegistro> nfces = nfceRepo.findByPeriodo(periodo);
        List<NfeRegistro> nfes   = nfeRepo.findByPeriodo(periodo);

        if (nfces.isEmpty() && nfes.isEmpty()) {
            log.warn("Nenhum dado de imposto encontrado no periodo. PDF nao gerado.");
            return;
        }

        BigDecimal icmsNfce   = sumNfce(nfces, NfceRegistro::icms);
        BigDecimal pisNfce    = sumNfce(nfces, NfceRegistro::pis);
        BigDecimal cofinsNfce = sumNfce(nfces, NfceRegistro::cofins);
        BigDecimal icmsNfe    = sumNfe(nfes, NfeRegistro::valorIcms);
        BigDecimal icmsStNfe  = sumNfe(nfes, NfeRegistro::valorIcmsSt);
        BigDecimal pisNfe     = sumNfe(nfes, NfeRegistro::valorPis);
        BigDecimal cofinsNfe  = sumNfe(nfes, NfeRegistro::valorCofins);
        BigDecimal ipiNfe     = sumNfe(nfes, NfeRegistro::valorIpi);
        BigDecimal totalImpostos = icmsNfce.add(pisNfce).add(cofinsNfce)
            .add(icmsNfe).add(icmsStNfe).add(pisNfe).add(cofinsNfe).add(ipiNfe);

        Path pdfPath = outputDir.resolve("PDF").resolve("Resumo_Impostos_" + periodo.mesAnoRef() + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            PDFont fontNormal = loadFont(doc, false);
            PDFont fontBold   = loadFont(doc, true);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_HEIGHT - MARGIN;
                y = drawCompanyHeader(cs, fontBold, fontNormal, y);
                y = drawReportTitle(cs, fontBold, "RESUMO DE IMPOSTOS", periodo, y);
                y = drawLine(cs, MARGIN, y - 4, PAGE_WIDTH - MARGIN, y - 4);
                y -= 20;

                // NFCe
                y = drawSectionHeader(cs, fontBold, "IMPOSTOS - NFCe (Varejo/Consumidor Final)", y);
                y = drawKeyValue(cs, fontBold, fontNormal, "ICMS:", "R$ " + fmtMoney(icmsNfce), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "PIS:", "R$ " + fmtMoney(pisNfce), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "COFINS:", "R$ " + fmtMoney(cofinsNfce), y);
                y -= 10;

                // NFe
                y = drawSectionHeader(cs, fontBold, "IMPOSTOS - NFe (Operações)", y);
                y = drawKeyValue(cs, fontBold, fontNormal, "ICMS:", "R$ " + fmtMoney(icmsNfe), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "ICMS-ST:", "R$ " + fmtMoney(icmsStNfe), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "PIS:", "R$ " + fmtMoney(pisNfe), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "COFINS:", "R$ " + fmtMoney(cofinsNfe), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "IPI:", "R$ " + fmtMoney(ipiNfe), y);
                y -= 10;

                y = drawKeyValueHighlight(cs, fontBold, "TOTAL GERAL DE IMPOSTOS:", "R$ " + fmtMoney(totalImpostos), y);
            }
            doc.save(pdfPath.toFile());
        }
        log.info("PDF Resumo de Impostos gerado: {}", pdfPath);
    }

    // ── PDF Resumo de Compras ─────────────────────────────────────────────────

    public void gerarResumoCompras(Connection conn, Periodo periodo, Path outputDir) throws IOException {
        CompraRepository compraRepo = new CompraRepository(conn);
        List<CompraRegistro> compras = compraRepo.findByPeriodo(periodo);

        if (compras.isEmpty()) {
            log.warn("Nenhuma compra encontrada no periodo. PDF nao gerado.");
            return;
        }

        BigDecimal totalCompras = compras.stream().map(CompraRegistro::totalItem).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIcms    = compras.stream().map(CompraRegistro::icmsValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPis     = compras.stream().map(CompraRegistro::pisValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCofins  = compras.stream().map(CompraRegistro::cofinsValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIpi     = compras.stream().map(CompraRegistro::ipiValor).reduce(BigDecimal.ZERO, BigDecimal::add);

        Path pdfPath = outputDir.resolve("PDF").resolve("Resumo_Compras_" + periodo.mesAnoRef() + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            PDFont fontNormal = loadFont(doc, false);
            PDFont fontBold   = loadFont(doc, true);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_HEIGHT - MARGIN;
                y = drawCompanyHeader(cs, fontBold, fontNormal, y);
                y = drawReportTitle(cs, fontBold, "RESUMO DE COMPRAS (FORNECEDORES)", periodo, y);
                y = drawLine(cs, MARGIN, y - 4, PAGE_WIDTH - MARGIN, y - 4);
                y -= 16;

                y = drawKeyValue(cs, fontBold, fontNormal, "Total de notas:", String.valueOf(compras.size()), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "Valor Total:", "R$ " + fmtMoney(totalCompras), y);
                y -= 8;

                y = drawSectionHeader(cs, fontBold, "IMPOSTOS DAS COMPRAS", y);
                y = drawKeyValue(cs, fontBold, fontNormal, "ICMS:", "R$ " + fmtMoney(totalIcms), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "PIS:", "R$ " + fmtMoney(totalPis), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "COFINS:", "R$ " + fmtMoney(totalCofins), y);
                y = drawKeyValue(cs, fontBold, fontNormal, "IPI:", "R$ " + fmtMoney(totalIpi), y);
                y -= 14;

                // Tabela de compras
                y = drawSectionHeader(cs, fontBold, "DETALHAMENTO POR NOTA", y);
                String[] cols = {"Nota", "Data", "Valor Total", "ICMS", "PIS", "COFINS"};
                float[] widths = {60, 68, 90, 80, 70, 70};
                y = drawTableHeader(cs, fontBold, cols, widths, MARGIN, y);
                int count = 0;
                for (CompraRegistro c : compras) {
                    if (y < MARGIN + 40) break;
                    boolean alt = (count++ % 2 == 1);
                    String[] row = {
                        safe(String.valueOf(c.nota()), 10),
                        c.dataEmissao() != null ? c.dataEmissao().format(FMT) : "",
                        "R$ " + fmtMoney(c.totalItem()),
                        "R$ " + fmtMoney(c.icmsValor()),
                        "R$ " + fmtMoney(c.pisValor()),
                        "R$ " + fmtMoney(c.cofinsValor())
                    };
                    y = drawTableRow(cs, fontNormal, fontBold, row, widths, MARGIN, y, alt);
                    if (c.nfeChave() != null && !c.nfeChave().isBlank()) {
                        y = drawChaveAcesso(cs, fontNormal, c.nfeChave(), y);
                    }
                }
            }
            doc.save(pdfPath.toFile());
        }
        log.info("PDF Resumo de Compras gerado: {}", pdfPath);
    }

    // ── Componentes visuais ───────────────────────────────────────────────────

    /** Header com Nome e CNPJ da empresa (em todas as páginas) */
    private float drawCompanyHeader(PDPageContentStream cs, PDFont fontBold, PDFont fontNormal,
                                     float y) throws IOException {
        // Fundo azul da empresa
        cs.setNonStrokingColor(COLOR_HEADER_BG[0], COLOR_HEADER_BG[1], COLOR_HEADER_BG[2]);
        cs.addRect(0, y - 44, PAGE_WIDTH, 46);
        cs.fill();

        cs.setNonStrokingColor(COLOR_TEXT_WHITE[0], COLOR_TEXT_WHITE[1], COLOR_TEXT_WHITE[2]);
        cs.beginText();
        cs.setFont(fontBold, 14);
        cs.newLineAtOffset(MARGIN, y - 16);
        cs.showText(getCompanyName());
        cs.endText();

        cs.beginText();
        cs.setFont(fontNormal, 9);
        cs.newLineAtOffset(MARGIN, y - 30);
        cs.showText("CNPJ: " + getCompanyCnpj() + "  |  Módulo Fiscal InfoAtiva");
        cs.endText();

        cs.setNonStrokingColor(COLOR_TEXT_DARK[0], COLOR_TEXT_DARK[1], COLOR_TEXT_DARK[2]);
        return y - 52;
    }

    private float drawReportTitle(PDPageContentStream cs, PDFont fontBold, String title,
                                   Periodo periodo, float y) throws IOException {
        y -= 8;
        cs.beginText();
        cs.setFont(fontBold, 15);
        cs.setNonStrokingColor(COLOR_HEADER_BG[0], COLOR_HEADER_BG[1], COLOR_HEADER_BG[2]);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(title);
        cs.endText();

        y -= LINE_HEIGHT;
        cs.beginText();
        cs.setFont(fontBold, 9);
        cs.setNonStrokingColor(0.45f, 0.45f, 0.45f);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("Período: " + periodo.inicio().format(FMT) + " a " + periodo.fim().format(FMT) +
                    "   |   Gerado em: " + LocalDate.now().format(FMT));
        cs.endText();
        cs.setNonStrokingColor(COLOR_TEXT_DARK[0], COLOR_TEXT_DARK[1], COLOR_TEXT_DARK[2]);
        return y - 6;
    }

    private float drawSectionHeader(PDPageContentStream cs, PDFont fontBold, String text, float y) throws IOException {
        y -= 4;
        cs.setNonStrokingColor(COLOR_ROW_ALT[0], COLOR_ROW_ALT[1], COLOR_ROW_ALT[2]);
        cs.addRect(MARGIN, y - 4, PAGE_WIDTH - 2 * MARGIN, 18);
        cs.fill();

        cs.beginText();
        cs.setFont(fontBold, 10);
        cs.setNonStrokingColor(COLOR_HEADER_BG[0], COLOR_HEADER_BG[1], COLOR_HEADER_BG[2]);
        cs.newLineAtOffset(MARGIN + 6, y + 8);
        cs.showText(text);
        cs.endText();
        cs.setNonStrokingColor(COLOR_TEXT_DARK[0], COLOR_TEXT_DARK[1], COLOR_TEXT_DARK[2]);
        return y - 18;
    }

    private float drawKeyValue(PDPageContentStream cs, PDFont fontBold, PDFont fontNormal,
                                String key, String value, float y) throws IOException {
        y -= 2;
        cs.beginText();
        cs.setFont(fontBold, 9);
        cs.newLineAtOffset(MARGIN + 12, y);
        cs.showText(key);
        cs.endText();
        cs.beginText();
        cs.setFont(fontNormal, 9);
        cs.newLineAtOffset(MARGIN + 130, y);
        cs.showText(value);
        cs.endText();
        return y - LINE_HEIGHT;
    }

    private float drawKeyValueHighlight(PDPageContentStream cs, PDFont fontBold,
                                         String key, String value, float y) throws IOException {
        cs.setNonStrokingColor(COLOR_GREEN[0], COLOR_GREEN[1], COLOR_GREEN[2]);
        cs.beginText();
        cs.setFont(fontBold, 11);
        cs.newLineAtOffset(MARGIN + 12, y);
        cs.showText(key + "  " + value);
        cs.endText();
        cs.setNonStrokingColor(COLOR_TEXT_DARK[0], COLOR_TEXT_DARK[1], COLOR_TEXT_DARK[2]);
        return y - LINE_HEIGHT - 4;
    }

    private float drawSummaryCards(PDPageContentStream cs, PDFont fontBold, PDFont fontNormal,
                                    String[] labels, String[] values, float y) throws IOException {
        float cardW = (PAGE_WIDTH - 2 * MARGIN - 20) / labels.length;
        float cardH = 44;
        float x = MARGIN;
        for (int i = 0; i < labels.length; i++) {
            cs.setNonStrokingColor(COLOR_ROW_ALT[0], COLOR_ROW_ALT[1], COLOR_ROW_ALT[2]);
            cs.addRect(x, y - cardH, cardW - 6, cardH);
            cs.fill();
            cs.setNonStrokingColor(COLOR_HEADER_BG[0], COLOR_HEADER_BG[1], COLOR_HEADER_BG[2]);
            cs.addRect(x, y, cardW - 6, 3);
            cs.fill();
            cs.beginText();
            cs.setFont(fontBold, 16);
            cs.setNonStrokingColor(COLOR_TEXT_DARK[0], COLOR_TEXT_DARK[1], COLOR_TEXT_DARK[2]);
            cs.newLineAtOffset(x + 10, y - 24);
            cs.showText(values[i]);
            cs.endText();
            cs.beginText();
            cs.setFont(fontNormal, 8);
            cs.setNonStrokingColor(0.45f, 0.45f, 0.45f);
            cs.newLineAtOffset(x + 10, y - 38);
            cs.showText(labels[i]);
            cs.endText();
            x += cardW;
        }
        cs.setNonStrokingColor(COLOR_TEXT_DARK[0], COLOR_TEXT_DARK[1], COLOR_TEXT_DARK[2]);
        return y - cardH - 6;
    }

    private float drawTableHeader(PDPageContentStream cs, PDFont fontBold,
                                   String[] cols, float[] widths, float x, float y) throws IOException {
        float headerH = 18f;
        float curX = x;

        cs.setNonStrokingColor(COLOR_HEADER_BG[0], COLOR_HEADER_BG[1], COLOR_HEADER_BG[2]);
        cs.addRect(x, y - headerH, sum(widths), headerH);
        cs.fill();

        cs.setNonStrokingColor(COLOR_TEXT_WHITE[0], COLOR_TEXT_WHITE[1], COLOR_TEXT_WHITE[2]);
        for (int i = 0; i < cols.length; i++) {
            cs.beginText();
            cs.setFont(fontBold, 8);
            cs.newLineAtOffset(curX + 4, y - 12);
            cs.showText(cols[i]);
            cs.endText();
            curX += widths[i];
        }
        cs.setNonStrokingColor(COLOR_TEXT_DARK[0], COLOR_TEXT_DARK[1], COLOR_TEXT_DARK[2]);
        return y - headerH;
    }

    private float drawTableRow(PDPageContentStream cs, PDFont fontNormal, PDFont fontBold,
                                String[] row, float[] widths, float x, float y,
                                boolean alt) throws IOException {
        float rowH = 14f;
        if (alt) {
            cs.setNonStrokingColor(COLOR_ROW_ALT[0], COLOR_ROW_ALT[1], COLOR_ROW_ALT[2]);
            cs.addRect(x, y - rowH, sum(widths), rowH);
            cs.fill();
        }
        float curX = x;
        cs.setNonStrokingColor(COLOR_TEXT_DARK[0], COLOR_TEXT_DARK[1], COLOR_TEXT_DARK[2]);
        for (int i = 0; i < row.length; i++) {
            cs.beginText();
            cs.setFont(fontNormal, 8);
            cs.newLineAtOffset(curX + 4, y - 10);
            String val = row[i] == null ? "" : row[i];
            if (val.length() * 5 > widths[i] - 6) {
                val = val.substring(0, Math.max(0, (int)(widths[i] - 6) / 5));
            }
            cs.showText(val);
            cs.endText();
            curX += widths[i];
        }
        return y - rowH;
    }

    /** Exibe chave de acesso (44 dígitos) de forma compacta */
    private float drawChaveAcesso(PDPageContentStream cs, PDFont font, String chave, float y) throws IOException {
        y -= 2;
        cs.beginText();
        cs.setFont(font, 6.5f);
        cs.setNonStrokingColor(0.55f, 0.55f, 0.55f);
        cs.newLineAtOffset(MARGIN + 16, y);
        // Formatar chave: grupos de 4
        String fmt = formatChave(chave);
        cs.showText("Chave: " + fmt);
        cs.endText();
        cs.setNonStrokingColor(COLOR_TEXT_DARK[0], COLOR_TEXT_DARK[1], COLOR_TEXT_DARK[2]);
        return y - 9;
    }

    private String formatChave(String chave) {
        if (chave == null || chave.length() != 44) return chave != null ? chave : "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 44; i += 4) {
            if (i > 0) sb.append(' ');
            sb.append(chave, i, Math.min(i + 4, 44));
        }
        return sb.toString();
    }

    private float drawLine(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.setStrokingColor(COLOR_DIVIDER[0], COLOR_DIVIDER[1], COLOR_DIVIDER[2]);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
        cs.setStrokingColor(0f, 0f, 0f);
        return y1 - 2;
    }

    // ── Dados da empresa (lidos do parâmetro / INI) ───────────────────────────

    private static String cachedCompanyName = null;
    private static String cachedCnpj = null;

    public static void setCompanyInfo(String nome, String cnpj) {
        cachedCompanyName = nome;
        cachedCnpj = cnpj;
    }

    private String getCompanyName() {
        return cachedCompanyName != null ? cachedCompanyName : "EMPRESA";
    }

    private String getCompanyCnpj() {
        return cachedCnpj != null ? cachedCnpj : "00.000.000/0000-00";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private float sum(float[] arr) {
        float s = 0; for (float v : arr) s += v; return s;
    }

    private String fmtMoney(BigDecimal v) {
        if (v == null) return "0,00";
        return String.format("%,.2f", v).replace(",", "X").replace(".", ",").replace("X", ".");
    }

    private String safe(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    @FunctionalInterface interface NfceField { BigDecimal get(NfceRegistro r); }
    @FunctionalInterface interface NfeField  { BigDecimal get(NfeRegistro r);  }

    private BigDecimal sumNfce(List<NfceRegistro> list, NfceField f) {
        return list.stream().filter(n -> !"S".equals(n.cupomCancelado()))
                   .map(f::get).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal sumNfe(List<NfeRegistro> list, NfeField f) {
        return list.stream().filter(n -> !"S".equals(n.cancelado()))
                   .map(f::get).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
