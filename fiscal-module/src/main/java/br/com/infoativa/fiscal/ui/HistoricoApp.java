package br.com.infoativa.fiscal.ui;

import br.com.infoativa.fiscal.xml.XmlCacheRepository;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela de Histórico de Fechamentos Fiscais.
 *
 * Consulta a tabela XML_PROCESSADOS com filtros:
 *   - Período (mês/ano início e fim)
 *   - Status de processamento (OK / ERRO / TODOS)
 *   - Tipo de documento (NFe / NFCe / TODOS)
 *   - Busca por chave de acesso
 *
 * Exibe:
 *   - Tabela paginada com 500 registros por página
 *   - Totalizadores (total XMLs, OK, ERRO, Contingência)
 *   - Botão exportar para CSV
 *   - Botão reprocessar XML selecionado
 */
public class HistoricoApp extends VBox {

    private static final Logger log = LoggerFactory.getLogger(HistoricoApp.class);
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DT   = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Connection conn;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "historico-bg"); t.setDaemon(true); return t;
    });

    // Filtros
    private ComboBox<String> cbMesIni, cbAnoIni, cbMesFin, cbAnoFin;
    private ComboBox<String> cbStatus, cbTipo;
    private TextField tfBusca;
    private Label lblTotal, lblOk, lblErro, lblContingencia;

    // Tabela
    private TableView<XmlEntry> tabela;
    private ObservableList<XmlEntry> dados = FXCollections.observableArrayList();

    // Paginação
    private int pagina = 0;
    private static final int PAGE_SIZE = 500;
    private Label lblPagina;

    public HistoricoApp(Connection conn) {
        this.conn = conn;
        setSpacing(0);
        setPadding(new Insets(0));
        getChildren().addAll(buildFiltros(), buildTotalizadores(), buildTabela(), buildBarraInferior());
    }

    // ── Filtros ───────────────────────────────────────────────────────────

    private VBox buildFiltros() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(20, 24, 16, 24));
        box.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        Label titulo = new Label("Histórico de XMLs Processados");
        titulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titulo.setStyle("-fx-text-fill: #1e293b;");

        Label sub = new Label("Consulte todos os XMLs registrados na tabela XML_PROCESSADOS");
        sub.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        // Linha de período
        HBox linhaData = new HBox(10);
        linhaData.setAlignment(Pos.CENTER_LEFT);

        Label lblDe = bold("Período: de");
        cbMesIni = combo(meses(), mes(LocalDate.now().minusMonths(1)));
        cbAnoIni = combo(anos(), ano(LocalDate.now()));
        Label lblAte = bold("até");
        cbMesFin = combo(meses(), mes(LocalDate.now()));
        cbAnoFin = combo(anos(), ano(LocalDate.now()));

        linhaData.getChildren().addAll(lblDe, cbMesIni, lblBarra("/"), cbAnoIni,
                                        lblAte, cbMesFin, lblBarra("/"), cbAnoFin);

        // Linha de filtros adicionais
        HBox linhaFiltros = new HBox(12);
        linhaFiltros.setAlignment(Pos.CENTER_LEFT);

        Label lblStatus = bold("Status:");
        cbStatus = combo(List.of("Todos", "OK", "ERRO"), "Todos");

        Label lblTipo = bold("Tipo:");
        cbTipo = combo(List.of("Todos", "NFe (55)", "NFCe (65)", "Contingência"), "Todos");

        Label lblBusca = bold("Chave:");
        tfBusca = new TextField();
        tfBusca.setPromptText("Parte da chave de acesso...");
        tfBusca.setPrefWidth(240);
        tfBusca.setStyle("-fx-border-color: #d1d5db; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 5 8;");

        Button btnBuscar = btn("🔍 Buscar", "#1a56db", "white");
        btnBuscar.setOnAction(e -> pesquisar());

        Button btnLimpar = btn("✕ Limpar", "#f3f4f6", "#374151");
        btnLimpar.setOnAction(e -> limparFiltros());

        Button btnExportar = btn("📥 Exportar CSV", "#057a55", "white");
        btnExportar.setOnAction(e -> exportarCsv());

        linhaFiltros.getChildren().addAll(lblStatus, cbStatus, lblTipo, cbTipo,
                                          lblBusca, tfBusca, btnBuscar, btnLimpar, btnExportar);

        box.getChildren().addAll(titulo, sub, linhaData, linhaFiltros);
        return box;
    }

    // ── Totalizadores ─────────────────────────────────────────────────────

    private HBox buildTotalizadores() {
        HBox box = new HBox(12);
        box.setPadding(new Insets(12, 24, 12, 24));
        box.setStyle("-fx-background-color: #f8fafc;");
        box.setAlignment(Pos.CENTER_LEFT);

        lblTotal       = cardTotal("Total XMLs",  "0",  "#1a56db");
        lblOk          = cardTotal("✓ OK",        "0",  "#057a55");
        lblErro        = cardTotal("✗ Erro",      "0",  "#c81e1e");
        lblContingencia = cardTotal("⚠ Contingência", "0", "#c27803");

        box.getChildren().addAll(lblTotal, lblOk, lblErro, lblContingencia);
        return box;
    }

    // ── Tabela ────────────────────────────────────────────────────────────

    private VBox buildTabela() {
        tabela = new TableView<>(dados);
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabela.getStyleClass().add("historico-table");
        VBox.setVgrow(tabela, Priority.ALWAYS);

        tabela.getColumns().addAll(
            col("Chave de Acesso",    "chave",              200),
            col("Emissão",            "dataEmissaoFmt",      80),
            col("Status NF",          "status",              80),
            col("Proc.",              "statusProc",          60),
            col("Atualizado em",      "dataAtualizacaoFmt", 110),
            col("Caminho",            "caminho",            220)
        );

        // Menu de contexto
        ContextMenu ctx = new ContextMenu();
        MenuItem miCopiar = new MenuItem("📋 Copiar Chave");
        miCopiar.setOnAction(e -> {
            XmlEntry sel = tabela.getSelectionModel().getSelectedItem();
            if (sel != null) {
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                    new javafx.scene.input.ClipboardContent() {{ putString(sel.getChave()); }}
                );
            }
        });
        MenuItem miAbrir = new MenuItem("📁 Abrir Pasta");
        miAbrir.setOnAction(e -> {
            XmlEntry sel = tabela.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getCaminho() != null && !sel.getCaminho().isBlank()) {
                try {
                    java.awt.Desktop.getDesktop().open(
                        new java.io.File(sel.getCaminho()).getParentFile());
                } catch (Exception ex) { log.warn("Erro ao abrir pasta: {}", ex.getMessage()); }
            }
        });
        MenuItem miMarcarErro = new MenuItem("✗ Marcar como ERRO");
        miMarcarErro.setOnAction(e -> marcarStatus("ERRO"));
        MenuItem miMarcarOk = new MenuItem("✓ Marcar como OK");
        miMarcarOk.setOnAction(e -> marcarStatus("OK"));
        ctx.getItems().addAll(miCopiar, miAbrir, new SeparatorMenuItem(), miMarcarOk, miMarcarErro);
        tabela.setContextMenu(ctx);

        // Colorir linhas por status
        tabela.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(XmlEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if ("ERRO".equals(item.getStatusProc())) {
                    setStyle("-fx-background-color: #fff5f5;");
                } else if (item.getStatus() != null && item.getStatus().contains("CONTINGENC")) {
                    setStyle("-fx-background-color: #fffbeb;");
                } else {
                    setStyle("");
                }
            }
        });

        VBox box = new VBox(tabela);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    // ── Barra inferior ────────────────────────────────────────────────────

    private HBox buildBarraInferior() {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(8, 24, 8, 24));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");

        Button btnAnterior = btn("◀ Anterior", "#f3f4f6", "#374151");
        btnAnterior.setOnAction(e -> { if (pagina > 0) { pagina--; pesquisar(); } });

        lblPagina = new Label("Página 1");
        lblPagina.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        Button btnProximo = btn("Próximo ▶", "#f3f4f6", "#374151");
        btnProximo.setOnAction(e -> { pagina++; pesquisar(); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label info = new Label("Clique com botão direito para opções");
        info.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

        bar.getChildren().addAll(btnAnterior, lblPagina, btnProximo, spacer, info);
        return bar;
    }

    // ── Pesquisa ──────────────────────────────────────────────────────────

    public void pesquisar() {
        String mesIni = cbMesIni.getValue();
        String anoIni = cbAnoIni.getValue();
        String mesFin = cbMesFin.getValue();
        String anoFin = cbAnoFin.getValue();

        if (mesIni == null || anoIni == null || mesFin == null || anoFin == null) return;

        LocalDate ini = LocalDate.of(Integer.parseInt(anoIni), Integer.parseInt(mesIni), 1);
        LocalDate fin = LocalDate.of(Integer.parseInt(anoFin), Integer.parseInt(mesFin), 1)
                .withDayOfMonth(LocalDate.of(Integer.parseInt(anoFin),
                        Integer.parseInt(mesFin), 1).lengthOfMonth());

        String status = cbStatus.getValue();
        String tipo   = cbTipo.getValue();
        String busca  = tfBusca.getText().trim();

        executor.submit(() -> {
            try {
                List<XmlEntry> resultados = executarConsulta(ini, fin, status, tipo, busca);
                Map<String, Long> totais  = calcularTotais(ini, fin);

                Platform.runLater(() -> {
                    dados.setAll(resultados);
                    lblTotal.setText(s(totais.getOrDefault("total", 0L)));
                    lblOk.setText(s(totais.getOrDefault("ok", 0L)));
                    lblErro.setText(s(totais.getOrDefault("erro", 0L)));
                    lblContingencia.setText(s(totais.getOrDefault("contingencia", 0L)));
                    lblPagina.setText("Página " + (pagina + 1) +
                            (resultados.size() == PAGE_SIZE ? " (mais...)" : ""));
                    log.info("Histórico: {} registros exibidos", resultados.size());
                });
            } catch (Exception e) {
                log.error("Erro ao pesquisar histórico", e);
                Platform.runLater(() -> dados.clear());
            }
        });
    }

    private List<XmlEntry> executarConsulta(LocalDate ini, LocalDate fin,
                                             String status, String tipo, String busca)
            throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT CHAVE, DATA_EMISSAO, CAMINHO, STATUS,
                   STATUS_PROCESSAMENTO, DATA_ATUALIZACAO
            FROM XML_PROCESSADOS
            WHERE DATA_EMISSAO >= ? AND DATA_EMISSAO <= ?
            """);

        List<Object> params = new ArrayList<>(List.of(Date.valueOf(ini), Date.valueOf(fin)));

        if (status != null && !"Todos".equals(status)) {
            sql.append(" AND STATUS_PROCESSAMENTO = ?");
            params.add(status);
        }

        if (tipo != null && !"Todos".equals(tipo)) {
            if (tipo.contains("NFe")) {
                sql.append(" AND CHAVE LIKE '%55%'");
            } else if (tipo.contains("NFCe")) {
                sql.append(" AND CHAVE LIKE '3%'"); // NFCe começa com cUF diferente
            } else if (tipo.contains("Contingência")) {
                sql.append(" AND (STATUS LIKE '%CONTINGENC%' OR STATUS LIKE '%CONTINGÊN%')");
            }
        }

        if (!busca.isEmpty()) {
            sql.append(" AND CHAVE LIKE ?");
            params.add("%" + busca + "%");
        }

        sql.append(" ORDER BY DATA_EMISSAO DESC, CHAVE ROWS ? TO ?");
        params.add(pagina * PAGE_SIZE + 1);
        params.add((pagina + 1) * PAGE_SIZE);

        List<XmlEntry> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Date d)    ps.setDate(i + 1, d);
                else if (p instanceof String str) ps.setString(i + 1, str);
                else if (p instanceof Integer iv) ps.setInt(i + 1, iv);
                else ps.setObject(i + 1, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date de = rs.getDate("DATA_EMISSAO");
                    Timestamp dt = rs.getTimestamp("DATA_ATUALIZACAO");
                    lista.add(new XmlEntry(
                        rs.getString("CHAVE"),
                        de != null ? de.toLocalDate().format(FMT_DATE) : "",
                        rs.getString("STATUS"),
                        rs.getString("STATUS_PROCESSAMENTO"),
                        dt != null ? dt.toLocalDateTime().format(FMT_DT) : "",
                        rs.getString("CAMINHO")
                    ));
                }
            }
        }
        return lista;
    }

    private Map<String, Long> calcularTotais(LocalDate ini, LocalDate fin) {
        Map<String, Long> map = new HashMap<>();
        String sql = """
            SELECT
                COUNT(*)                                            AS TOTAL,
                SUM(CASE WHEN STATUS_PROCESSAMENTO = 'OK' THEN 1 ELSE 0 END) AS OK_,
                SUM(CASE WHEN STATUS_PROCESSAMENTO = 'ERRO' THEN 1 ELSE 0 END) AS ERRO_,
                SUM(CASE WHEN STATUS LIKE '%CONTINGENC%' THEN 1 ELSE 0 END) AS CONT_
            FROM XML_PROCESSADOS
            WHERE DATA_EMISSAO >= ? AND DATA_EMISSAO <= ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(ini));
            ps.setDate(2, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    map.put("total",       rs.getLong("TOTAL"));
                    map.put("ok",          rs.getLong("OK_"));
                    map.put("erro",        rs.getLong("ERRO_"));
                    map.put("contingencia", rs.getLong("CONT_"));
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao calcular totais do histórico: {}", e.getMessage());
        }
        return map;
    }

    private void marcarStatus(String novoStatus) {
        XmlEntry sel = tabela.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            String sql = "UPDATE XML_PROCESSADOS SET STATUS_PROCESSAMENTO = ?, " +
                         "DATA_ATUALIZACAO = ? WHERE CHAVE = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, novoStatus);
                ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(3, sel.getChave());
                ps.executeUpdate();
                conn.commit();
            }
            pesquisar();
        } catch (Exception e) {
            log.error("Erro ao marcar status {}", novoStatus, e);
        }
    }

    private void exportarCsv() {
        if (dados.isEmpty()) return;
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            java.io.File f = new java.io.File(System.getProperty("user.dir"),
                "historico_" + ts + ".csv");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(f), java.nio.charset.StandardCharsets.UTF_8))) {
                pw.println("Chave;DataEmissao;Status;StatusProc;DataAtualizacao;Caminho");
                for (XmlEntry e : dados) {
                    pw.println(e.getChave() + ";" + e.getDataEmissaoFmt() + ";" +
                               e.getStatus() + ";" + e.getStatusProc() + ";" +
                               e.getDataAtualizacaoFmt() + ";" + e.getCaminho());
                }
            }
            log.info("CSV exportado: {}", f.getAbsolutePath());
            try { java.awt.Desktop.getDesktop().open(f.getParentFile()); } catch (Exception ignored) {}
        } catch (Exception e) {
            log.error("Erro ao exportar CSV", e);
        }
    }

    private void limparFiltros() {
        cbStatus.setValue("Todos");
        cbTipo.setValue("Todos");
        tfBusca.clear();
        pagina = 0;
        pesquisar();
    }

    // ── Helpers UI ────────────────────────────────────────────────────────

    private Label cardTotal(String titulo, String valor, String cor) {
        VBox card = new VBox(3);
        card.setPadding(new Insets(8, 16, 8, 16));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8px;" +
                      "-fx-border-color: " + cor + "; -fx-border-width: 0 0 0 4px;" +
                      "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 4, 0, 0, 1);");
        Label lTit = new Label(titulo);
        lTit.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
        Label lVal = new Label(valor);
        lVal.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        lVal.setStyle("-fx-text-fill: " + cor + ";");
        card.getChildren().addAll(lTit, lVal);
        // Guardar referência na label de valor
        return lVal;
    }

    @SuppressWarnings("unchecked")
    private TableColumn<XmlEntry, String> col(String titulo, String propriedade, double pref) {
        TableColumn<XmlEntry, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(new PropertyValueFactory<>(propriedade));
        c.setPrefWidth(pref);
        return c;
    }

    private ComboBox<String> combo(List<String> itens, String valor) {
        ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList(itens));
        cb.setValue(valor);
        cb.setStyle("-fx-font-size: 12px; -fx-padding: 3;");
        return cb;
    }

    private Button btn(String texto, String bg, String fg) {
        Button b = new Button(texto);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + ";" +
                   "-fx-font-size: 12px; -fx-padding: 6 14; -fx-background-radius: 6px; -fx-cursor: hand;");
        return b;
    }

    private Label bold(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        l.setStyle("-fx-text-fill: #374151;");
        return l;
    }

    private Label lblBarra(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill: #9ca3af;");
        return l;
    }

    private List<String> meses() {
        List<String> m = new ArrayList<>();
        for (int i = 1; i <= 12; i++) m.add(String.format("%02d", i));
        return m;
    }

    private List<String> anos() {
        List<String> a = new ArrayList<>();
        int ano = LocalDate.now().getYear();
        for (int y = ano; y >= ano - 5; y--) a.add(String.valueOf(y));
        return a;
    }

    private String mes(LocalDate d) { return String.format("%02d", d.getMonthValue()); }
    private String ano(LocalDate d) { return String.valueOf(d.getYear()); }
    private String s(Object o)      { return o == null ? "0" : o.toString(); }

    // ── Model da tabela ───────────────────────────────────────────────────

    public static class XmlEntry {
        private final String chave, dataEmissaoFmt, status, statusProc, dataAtualizacaoFmt, caminho;

        public XmlEntry(String chave, String dataEmissaoFmt, String status,
                         String statusProc, String dataAtualizacaoFmt, String caminho) {
            this.chave             = chave != null ? chave : "";
            this.dataEmissaoFmt    = dataEmissaoFmt != null ? dataEmissaoFmt : "";
            this.status            = status != null ? status : "";
            this.statusProc        = statusProc != null ? statusProc : "";
            this.dataAtualizacaoFmt = dataAtualizacaoFmt != null ? dataAtualizacaoFmt : "";
            this.caminho           = caminho != null ? caminho : "";
        }

        public String getChave()              { return chave; }
        public String getDataEmissaoFmt()     { return dataEmissaoFmt; }
        public String getStatus()             { return status; }
        public String getStatusProc()         { return statusProc; }
        public String getDataAtualizacaoFmt() { return dataAtualizacaoFmt; }
        public String getCaminho()            { return caminho; }
    }
}
