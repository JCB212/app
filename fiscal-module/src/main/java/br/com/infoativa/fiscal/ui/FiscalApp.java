package br.com.infoativa.fiscal.ui;

import br.com.infoativa.fiscal.config.AppConfig;
import br.com.infoativa.fiscal.config.IniManager;
import br.com.infoativa.fiscal.db.DatabaseGateway;
import br.com.infoativa.fiscal.db.FirebirdConnectionFactory;
import br.com.infoativa.fiscal.domain.*;
import br.com.infoativa.fiscal.mail.EmailService;
import br.com.infoativa.fiscal.repository.ParametrosRepository;
import br.com.infoativa.fiscal.service.ClosingOrchestrator;
import br.com.infoativa.fiscal.service.PeriodService;
import br.com.infoativa.fiscal.service.ProcessingMode;
import br.com.infoativa.fiscal.tray.FiscalToastNotification;
import br.com.infoativa.fiscal.tray.SystemTrayManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FiscalApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(FiscalApp.class);

    // ── Estado ────────────────────────────────────────────────────────────────
    private AppConfig appConfig;
    private IniManager iniManager;
    private DatabaseGateway dbGateway;
    private Stage primaryStage;
    private StackPane contentArea;
    private String activeMenu = "home";
    private String nomeEmpresa = "Empresa";
    private String cnpjEmpresa = "";
    private String usuarioLogado = "Admin";

    // Executor para tarefas em background
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fiscal-bg");
        t.setDaemon(true);
        return t;
    });

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        iniManager = new IniManager();

        // Modo tray: iniciar minimizado se --tray
        boolean startTray = getParameters().getRaw().contains("--tray");

        loadConfig();
        if (!authenticate(stage)) { Platform.exit(); return; }
        tryLoadCompanyInfo();

        // Construir UI
        BorderPane root = buildRoot();
        Scene scene = new Scene(root, 1150, 720);
        loadStylesheet(scene);

        stage.setTitle("Módulo Fiscal – InfoAtiva");
        stage.setScene(scene);
        stage.setMinWidth(950);
        stage.setMinHeight(620);

        // Interceptar fechamento → minimizar para tray
        stage.setOnCloseRequest(e -> {
            e.consume();
            if (SystemTrayManager.getInstance().isSupported()) {
                SystemTrayManager.getInstance().hideToTray();
            } else {
                confirmExit(stage);
            }
        });

        // Inicializar System Tray
        initSystemTray(stage);

        if (startTray) {
            Platform.setImplicitExit(false);
            log.info("Iniciando em modo tray");
        } else {
            stage.show();
            showHome();
        }
    }

    // ── Configuração e autenticação ───────────────────────────────────────────

    private void loadConfig() {
        try {
            appConfig = iniManager.loadOrCreate();
        } catch (IOException e) {
            log.error("Erro ao carregar configuração", e);
            appConfig = defaultConfig();
        }
    }

    private boolean authenticate(Stage stage) {
        if (!appConfig.utilizaSenha()) return true;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Módulo Fiscal – Autenticação");
        dialog.setHeaderText("Acesso protegido por senha");
        dialog.setContentText("Senha de acesso:");
        dialog.initOwner(stage);
        ((PasswordField) dialog.getEditor());

        var result = dialog.showAndWait();
        if (result.isEmpty() || !result.get().equals(appConfig.senhaAcesso())) {
            log.warn("Acesso negado: senha incorreta");
            return false;
        }
        return true;
    }

    private void tryLoadCompanyInfo() {
        if (dbGateway != null) return;
        try {
            dbGateway = DatabaseGateway.open(appConfig);
            ParametrosRepository pr = new ParametrosRepository(dbGateway.getConnection());
            nomeEmpresa = pr.getNomeEmpresa();
            cnpjEmpresa = pr.getCnpj();
            usuarioLogado = "Admin";
        } catch (Exception e) {
            log.warn("Banco de dados não conectado na inicialização: {}", e.getMessage());
        }
    }

    private void initSystemTray(Stage stage) {
        SystemTrayManager tray = SystemTrayManager.getInstance();
        tray.initialize(appConfig,
            () -> {
                Platform.setImplicitExit(true);
                stage.show();
                stage.toFront();
                showHome();
            },
            () -> {
                Platform.setImplicitExit(false);
                stage.hide();
            }
        );
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildHeaderBar());
        root.setLeft(buildSidebar());
        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        root.setCenter(contentArea);
        root.setBottom(buildStatusBar());
        return root;
    }

    private HBox buildHeaderBar() {
        HBox bar = new HBox(16);
        bar.getStyleClass().add("header-bar");
        bar.setPadding(new Insets(0, 20, 0, 20));
        bar.setAlignment(Pos.CENTER_LEFT);

        Label company = new Label(nomeEmpresa);
        company.getStyleClass().add("header-company");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label user = new Label("👤 " + usuarioLogado);
        user.getStyleClass().add("header-user");

        // Botão minimizar para tray
        Button trayBtn = new Button("⬇ Bandeja");
        trayBtn.getStyleClass().addAll("btn-small", "btn-outline");
        trayBtn.setTooltip(new Tooltip("Minimizar para a bandeja do sistema"));
        trayBtn.setOnAction(e -> SystemTrayManager.getInstance().hideToTray());

        bar.getChildren().addAll(company, spacer, user, trayBtn);
        return bar;
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(0);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(265);

        // Logo
        VBox logoBox = new VBox(4);
        logoBox.getStyleClass().add("sidebar-logo");
        logoBox.setPadding(new Insets(26, 20, 18, 20));
        Label logo = new Label("INFOATIVA");
        logo.getStyleClass().add("logo-text");
        Label sub = new Label("Módulo Fiscal");
        sub.getStyleClass().add("logo-subtitle");
        Label ver = new Label("v2.0.0");
        ver.getStyleClass().add("version-label");
        logoBox.getChildren().addAll(logo, sub, ver);

        Region sep = new Region();
        sep.getStyleClass().add("sidebar-separator");
        sep.setPrefHeight(1);

        // Menu items
        VBox menu = new VBox(3);
        menu.setPadding(new Insets(14, 10, 14, 10));
        menu.setId("sidebar-menu");

        Button btnHome    = menuBtn("Início",              ICON_HOME,    "home");
        Button btnProcess = menuBtn("Gerar e Enviar",      ICON_REPORT,  "process");
        Button btnReproc  = menuBtn("Reprocessar Mês",     ICON_REFRESH, "reprocess");
        Button btnDest    = menuBtn("Destinatários",       ICON_EMAIL,   "dest");
        Button btnEmail   = menuBtn("Config. E-mail",      ICON_SHIELD,  "email");
        Button btnConfig  = menuBtn("Configurações",       ICON_SETTINGS,"config");

        menu.getChildren().addAll(btnHome, btnProcess, btnReproc, btnDest, btnEmail, btnConfig);

        // Painel de status DB
        VBox dbPanel = new VBox(5);
        dbPanel.setPadding(new Insets(14, 18, 18, 18));
        VBox.setVgrow(dbPanel, Priority.ALWAYS);
        dbPanel.setAlignment(Pos.BOTTOM_LEFT);

        Label dbTitle = new Label("Banco de Dados");
        dbTitle.getStyleClass().add("db-status-title");
        Label dbInfo = new Label(appConfig.ipServidor() + ":" + appConfig.porta());
        dbInfo.getStyleClass().add("db-status-info");

        Button btnTestDb = new Button("Testar Conexão");
        btnTestDb.getStyleClass().addAll("btn-small", "btn-outline");
        btnTestDb.setOnAction(e -> testDbConnection());

        dbPanel.getChildren().addAll(dbTitle, dbInfo, btnTestDb);
        sidebar.getChildren().addAll(logoBox, sep, menu, dbPanel);
        return sidebar;
    }

    private Button menuBtn(String label, String svgContent, String id) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgContent);
        icon.setFill(javafx.scene.paint.Color.web("#94a3b8"));
        icon.setScaleX(0.78);
        icon.setScaleY(0.78);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("menu-label");

        HBox content = new HBox(12);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(icon, lbl);

        Button btn = new Button();
        btn.setGraphic(content);
        btn.getStyleClass().add("sidebar-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setUserData(id);

        btn.setOnAction(e -> {
            activeMenu = id;
            updateMenuHighlight();
            navigateTo(id);
        });
        return btn;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(4, 16, 4, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        Label status = new Label("✓ Sistema pronto");
        status.setId("main-status-label");
        status.getStyleClass().add("status-text");
        bar.getChildren().add(status);
        return bar;
    }

    // ── Navegação ─────────────────────────────────────────────────────────────

    private void navigateTo(String id) {
        contentArea.getChildren().clear();
        switch (id) {
            case "home"      -> showHome();
            case "process"   -> showProcessing();
            case "reprocess" -> showReprocess();
            case "dest"      -> showDestinatarios();
            case "email"     -> showEmailConfig();
            case "config"    -> showConfig();
        }
    }

    private void updateMenuHighlight() {
        // Buscar o VBox do menu pelo id
        VBox menu = (VBox) primaryStage.getScene().lookup("#sidebar-menu");
        if (menu == null) return;
        for (var node : menu.getChildren()) {
            if (node instanceof Button btn) {
                btn.getStyleClass().removeAll("sidebar-btn-active");
                if (activeMenu.equals(btn.getUserData())) {
                    btn.getStyleClass().add("sidebar-btn-active");
                }
            }
        }
    }

    // ── Telas ─────────────────────────────────────────────────────────────────

    private void showHome() {
        VBox pane = new VBox(20);
        pane.setPadding(new Insets(30));
        pane.getStyleClass().add("content-pane");

        Label title = new Label("Bem-vindo, " + usuarioLogado);
        title.getStyleClass().add("page-title");

        Label sub = new Label("Módulo Fiscal InfoAtiva – " + nomeEmpresa);
        sub.getStyleClass().add("page-subtitle");

        // Cards de resumo
        HBox cards = new HBox(16);
        cards.getChildren().addAll(
            buildInfoCard("🏢 Empresa", nomeEmpresa),
            buildInfoCard("📅 Período Atual",
                java.time.format.DateTimeFormatter.ofPattern("MM/yyyy").format(LocalDate.now())),
            buildInfoCard("🔗 Banco", appConfig.ipServidor() + ":" + appConfig.porta()),
            buildInfoCard("📬 E-mail",
                appConfig.emailDestinatarios().isEmpty() ? "Não configurado"
                : appConfig.emailDestinatarios().size() + " destinatário(s)")
        );

        // Ações rápidas
        Label actTitle = new Label("Ações Rápidas");
        actTitle.getStyleClass().add("section-title");

        HBox actions = new HBox(12);
        Button btnGenerate = new Button("⚡ Gerar e Enviar");
        btnGenerate.getStyleClass().addAll("btn-primary", "btn-large");
        btnGenerate.setOnAction(e -> { activeMenu = "process"; updateMenuHighlight(); showProcessing(); });

        Button btnConfig = new Button("⚙ Configurações");
        btnConfig.getStyleClass().addAll("btn-secondary", "btn-large");
        btnConfig.setOnAction(e -> { activeMenu = "config"; updateMenuHighlight(); showConfig(); });

        Button btnReproc = new Button("🔄 Reprocessar");
        btnReproc.getStyleClass().addAll("btn-outline", "btn-large");
        btnReproc.setOnAction(e -> { activeMenu = "reprocess"; updateMenuHighlight(); showReprocess(); });

        actions.getChildren().addAll(btnGenerate, btnConfig, btnReproc);

        pane.getChildren().addAll(title, sub, cards, actTitle, actions);
        contentArea.getChildren().setAll(pane);
    }

    private void showProcessing() {
        VBox pane = new VBox(18);
        pane.setPadding(new Insets(30));
        pane.getStyleClass().add("content-pane");

        Label title = new Label("Gerar e Enviar Fechamento Fiscal");
        title.getStyleClass().add("page-title");

        // Seleção de período
        HBox periodBox = new HBox(12);
        periodBox.setAlignment(Pos.CENTER_LEFT);
        Label lbPeriod = new Label("Período:");
        lbPeriod.getStyleClass().add("field-label");

        ComboBox<String> cbMes = new ComboBox<>();
        for (int m = 1; m <= 12; m++) cbMes.getItems().add(String.format("%02d", m));
        cbMes.setValue(String.format("%02d", LocalDate.now().minusMonths(1).getMonthValue()));

        ComboBox<String> cbAno = new ComboBox<>();
        int ano = LocalDate.now().getYear();
        for (int y = ano; y >= ano - 3; y--) cbAno.getItems().add(String.valueOf(y));
        cbAno.setValue(String.valueOf(LocalDate.now().minusMonths(1).getYear()));

        periodBox.getChildren().addAll(lbPeriod, cbMes, new Label("/"), cbAno);

        // Filtros do usuário
        TitledPane filterPane = new TitledPane("Filtros de Documento", buildFilterPanel());
        filterPane.setExpanded(true);
        filterPane.getStyleClass().add("filter-pane");

        CheckBox cbNfe = (CheckBox) filterPane.getContent().lookup("#chk-nfe");
        CheckBox cbNfce = (CheckBox) filterPane.getContent().lookup("#chk-nfce");
        CheckBox cbCompras = (CheckBox) filterPane.getContent().lookup("#chk-compras");
        CheckBox cbSped = (CheckBox) filterPane.getContent().lookup("#chk-sped");
        CheckBox cbSintegra = (CheckBox) filterPane.getContent().lookup("#chk-sintegra");
        CheckBox cbEnviarEmail = (CheckBox) filterPane.getContent().lookup("#chk-email");

        // Log de progresso
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        logArea.getStyleClass().add("log-area");
        logArea.setWrapText(true);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // Botão processar
        Button btnProcessar = new Button("▶ Iniciar Processamento");
        btnProcessar.getStyleClass().addAll("btn-primary", "btn-large");
        btnProcessar.setMaxWidth(Double.MAX_VALUE);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("progress-bar-fiscal");

        btnProcessar.setOnAction(e -> {
            String mes = cbMes.getValue();
            String anoStr = cbAno.getValue();
            if (mes == null || anoStr == null) {
                showAlert("Selecione o período antes de processar.", Alert.AlertType.WARNING);
                return;
            }

            int m = Integer.parseInt(mes);
            int y = Integer.parseInt(anoStr);
            YearMonth ym = YearMonth.of(y, m);
            Periodo periodo = new Periodo(ym.atDay(1), ym.atEndOfMonth());

            boolean nfe = cbNfe != null && cbNfe.isSelected();
            boolean nfce = cbNfce != null && cbNfce.isSelected();
            boolean compras = cbCompras != null && cbCompras.isSelected();
            boolean sped = cbSped != null && cbSped.isSelected();
            boolean sintegra = cbSintegra != null && cbSintegra.isSelected();
            boolean enviarEmail = cbEnviarEmail != null && cbEnviarEmail.isSelected();

            btnProcessar.setDisable(true);
            progressBar.setVisible(true);
            progressBar.setProgress(-1); // indeterminate
            logArea.clear();

            runInBackground(() -> {
                try {
                    ensureDbConnected();
                    ClosingOrchestrator orc = new ClosingOrchestrator(appConfig, dbGateway)
                        .comFiltros(nfe, nfce, compras, sped, sintegra)
                        .comEmpresa(nomeEmpresa, cnpjEmpresa);

                    ProcessamentoResult result = orc.execute(periodo, enviarEmail,
                        msg -> appendLog(logArea, msg));

                    Platform.runLater(() -> {
                        progressBar.setProgress(1);
                        btnProcessar.setDisable(false);
                        appendLog(logArea, "\n" + result.resumo());

                        if (result.sucesso() && enviarEmail) {
                            SystemTrayManager.getInstance().showArquivosEnviadosPopup();
                        }
                        if (!result.sucesso()) {
                            showAlert(result.mensagem(), Alert.AlertType.WARNING);
                        }
                    });
                } catch (Exception ex) {
                    log.error("Erro no processamento", ex);
                    Platform.runLater(() -> {
                        progressBar.setProgress(0);
                        btnProcessar.setDisable(false);
                        appendLog(logArea, "❌ ERRO: " + ex.getMessage());
                        showAlert("Erro no processamento: " + ex.getMessage(), Alert.AlertType.ERROR);
                    });
                }
            });
        });

        pane.getChildren().addAll(title, periodBox, filterPane, progressBar, btnProcessar,
                                   new Label("Log de Execução:"), logArea);
        contentArea.getChildren().setAll(new ScrollPane(pane) {{
            setFitToWidth(true);
            setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        }});
    }

    private VBox buildFilterPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));

        HBox row1 = new HBox(20);
        CheckBox chkNfe = new CheckBox("NFe (Saída)"); chkNfe.setId("chk-nfe"); chkNfe.setSelected(true);
        CheckBox chkNfce = new CheckBox("NFCe (Cupom)"); chkNfce.setId("chk-nfce"); chkNfce.setSelected(true);
        CheckBox chkCompras = new CheckBox("Compras (Entrada)"); chkCompras.setId("chk-compras"); chkCompras.setSelected(true);
        row1.getChildren().addAll(chkNfe, chkNfce, chkCompras);

        HBox row2 = new HBox(20);
        CheckBox chkSped = new CheckBox("Gerar SPED"); chkSped.setId("chk-sped"); chkSped.setSelected(true);
        CheckBox chkSintegra = new CheckBox("Gerar SINTEGRA"); chkSintegra.setId("chk-sintegra"); chkSintegra.setSelected(true);
        CheckBox chkEmail = new CheckBox("Enviar por E-mail"); chkEmail.setId("chk-email"); chkEmail.setSelected(true);
        row2.getChildren().addAll(chkSped, chkSintegra, chkEmail);

        panel.getChildren().addAll(row1, row2);
        return panel;
    }

    private void showReprocess() {
        VBox pane = new VBox(18);
        pane.setPadding(new Insets(30));
        pane.getStyleClass().add("content-pane");

        Label title = new Label("Reprocessar Mês");
        title.getStyleClass().add("page-title");

        Label desc = new Label("Limpa o cache do período selecionado e reprocessa todos os XMLs do zero.");
        desc.getStyleClass().add("page-subtitle");
        desc.setWrapText(true);

        // Período
        HBox periodBox = new HBox(12);
        periodBox.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> cbMes = new ComboBox<>();
        for (int m = 1; m <= 12; m++) cbMes.getItems().add(String.format("%02d", m));
        cbMes.setValue(String.format("%02d", LocalDate.now().getMonthValue()));

        ComboBox<String> cbAno = new ComboBox<>();
        int ano = LocalDate.now().getYear();
        for (int y = ano; y >= ano - 3; y--) cbAno.getItems().add(String.valueOf(y));
        cbAno.setValue(String.valueOf(ano));

        periodBox.getChildren().addAll(new Label("Período:"), cbMes, new Label("/"), cbAno);

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(220);
        logArea.getStyleClass().add("log-area");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        Button btnReproc = new Button("🔄 Reprocessar Período");
        btnReproc.getStyleClass().addAll("btn-warning", "btn-large");
        btnReproc.setMaxWidth(Double.MAX_VALUE);

        ProgressBar progress = new ProgressBar(0);
        progress.setVisible(false);
        progress.setMaxWidth(Double.MAX_VALUE);

        btnReproc.setOnAction(e -> {
            int m = Integer.parseInt(cbMes.getValue());
            int y = Integer.parseInt(cbAno.getValue());
            YearMonth ym = YearMonth.of(y, m);
            Periodo periodo = new Periodo(ym.atDay(1), ym.atEndOfMonth());

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Tem certeza que deseja reprocessar " + periodo.descricao() + "?\n" +
                "O cache do período será limpo.", ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Confirmar Reprocessamento");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    btnReproc.setDisable(true);
                    progress.setVisible(true);
                    progress.setProgress(-1);
                    logArea.clear();

                    runInBackground(() -> {
                        try {
                            ensureDbConnected();
                            ClosingOrchestrator orc = new ClosingOrchestrator(appConfig, dbGateway)
                                    .comEmpresa(nomeEmpresa, cnpjEmpresa);
                            ProcessamentoResult result = orc.reprocessarMes(periodo,
                                msg -> appendLog(logArea, msg));
                            Platform.runLater(() -> {
                                progress.setProgress(1);
                                btnReproc.setDisable(false);
                                appendLog(logArea, "\n" + result.resumo());
                            });
                        } catch (Exception ex) {
                            log.error("Erro ao reprocessar", ex);
                            Platform.runLater(() -> {
                                progress.setProgress(0);
                                btnReproc.setDisable(false);
                                appendLog(logArea, "❌ ERRO: " + ex.getMessage());
                            });
                        }
                    });
                }
            });
        });

        pane.getChildren().addAll(title, desc, periodBox, progress, btnReproc,
                                   new Label("Log:"), logArea);
        contentArea.getChildren().setAll(new ScrollPane(pane) {{
            setFitToWidth(true);
            setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        }});
    }

    private void showDestinatarios() {
        VBox pane = new VBox(16);
        pane.setPadding(new Insets(30));
        pane.getStyleClass().add("content-pane");

        Label title = new Label("Destinatários do E-mail");
        title.getStyleClass().add("page-title");

        Label sub = new Label("E-mails que receberão o fechamento fiscal. Separe múltiplos com vírgula.");
        sub.getStyleClass().add("page-subtitle");
        sub.setWrapText(true);

        ListView<String> listView = new ListView<>();
        listView.getItems().addAll(appConfig.emailDestinatarios());
        listView.setPrefHeight(200);
        listView.getStyleClass().add("dest-list");

        HBox addBox = new HBox(10);
        TextField tfEmail = new TextField();
        tfEmail.setPromptText("email@exemplo.com");
        tfEmail.getStyleClass().add("text-field-fiscal");
        HBox.setHgrow(tfEmail, Priority.ALWAYS);

        Button btnAdd = new Button("➕ Adicionar");
        btnAdd.getStyleClass().add("btn-primary");
        btnAdd.setOnAction(e -> {
            String email = tfEmail.getText().trim();
            if (!email.isBlank() && email.contains("@")) {
                listView.getItems().add(email);
                tfEmail.clear();
            } else {
                showAlert("Digite um e-mail válido.", Alert.AlertType.WARNING);
            }
        });

        Button btnRemove = new Button("🗑 Remover");
        btnRemove.getStyleClass().add("btn-danger");
        btnRemove.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) listView.getItems().remove(sel);
        });

        addBox.getChildren().addAll(tfEmail, btnAdd, btnRemove);

        Button btnSave = new Button("💾 Salvar Destinatários");
        btnSave.getStyleClass().addAll("btn-success", "btn-large");
        btnSave.setMaxWidth(Double.MAX_VALUE);
        btnSave.setOnAction(e -> {
            try {
                List<String> emails = new ArrayList<>(listView.getItems());
                iniManager.saveDestinatarios(emails);
                appConfig = iniManager.loadOrCreate();
                showSuccessAlert("Destinatários salvos com sucesso!");
            } catch (Exception ex) {
                log.error("Erro ao salvar destinatários", ex);
                showAlert("Erro ao salvar: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });

        pane.getChildren().addAll(title, sub, addBox, listView, btnSave);
        contentArea.getChildren().setAll(pane);
    }

    private void showEmailConfig() {
        VBox pane = new VBox(16);
        pane.setPadding(new Insets(30));
        pane.getStyleClass().add("content-pane");

        Label title = new Label("Configuração de E-mail SMTP");
        title.getStyleClass().add("page-title");

        EmailConfig ec = appConfig.emailConfig();

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.getStyleClass().add("form-grid");

        TextField tfHost = addField(grid, "Servidor SMTP:", ec.host(), 0);
        TextField tfPorta = addField(grid, "Porta:", String.valueOf(ec.port()), 1);
        TextField tfUser = addField(grid, "Usuário:", ec.usuario(), 2);
        PasswordField tfSenha = new PasswordField();
        tfSenha.setText(ec.senha());
        tfSenha.getStyleClass().add("text-field-fiscal");
        grid.add(new Label("Senha:"), 0, 3);
        grid.add(tfSenha, 1, 3);

        // Seleção modo SSL/STARTTLS
        HBox modeBox = new HBox(16);
        modeBox.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup tg = new ToggleGroup();
        RadioButton rbSsl = new RadioButton("SSL (porta 465)");
        rbSsl.setToggleGroup(tg); rbSsl.setSelected(ec.ssl() || ec.port() == 465);
        RadioButton rbTls = new RadioButton("STARTTLS (porta 587)");
        rbTls.setToggleGroup(tg); rbTls.setSelected(!ec.ssl() || ec.port() == 587);
        modeBox.getChildren().addAll(new Label("Modo:"), rbSsl, rbTls);

        // Auto-preencher porta ao mudar modo
        rbSsl.setOnAction(e -> tfPorta.setText("465"));
        rbTls.setOnAction(e -> tfPorta.setText("587"));

        // Nota de segurança
        Label secNote = new Label("ℹ️ A senha é salva no arquivo xmlContador.ini. Use uma senha de app se usar Gmail/Outlook.");
        secNote.getStyleClass().add("note-label");
        secNote.setWrapText(true);

        HBox btnBox = new HBox(12);

        Button btnTest = new Button("🔌 Testar Conexão SMTP");
        btnTest.getStyleClass().add("btn-secondary");
        btnTest.setOnAction(e -> {
            try {
                int port = Integer.parseInt(tfPorta.getText().trim());
                boolean ssl = rbSsl.isSelected();
                EmailConfig testConfig = new EmailConfig(
                    tfHost.getText().trim(), port, tfUser.getText().trim(), tfSenha.getText(), ssl);
                btnTest.setDisable(true);
                btnTest.setText("Testando...");
                runInBackground(() -> {
                    boolean ok = EmailService.testConnection(testConfig);
                    Platform.runLater(() -> {
                        btnTest.setDisable(false);
                        btnTest.setText("🔌 Testar Conexão SMTP");
                        if (ok) showSuccessAlert("✅ Conexão SMTP realizada com sucesso!");
                        else showAlert("❌ Falha na conexão SMTP.\nVerifique host, porta, usuário e senha.\nErro 535 = credenciais inválidas.", Alert.AlertType.ERROR);
                    });
                });
            } catch (NumberFormatException ex) {
                showAlert("Porta inválida.", Alert.AlertType.WARNING);
            }
        });

        Button btnSave = new Button("💾 Salvar Configurações");
        btnSave.getStyleClass().add("btn-primary");
        btnSave.setOnAction(e -> {
            try {
                int port = Integer.parseInt(tfPorta.getText().trim());
                boolean ssl = rbSsl.isSelected();
                EmailConfig newConfig = new EmailConfig(
                    tfHost.getText().trim(), port, tfUser.getText().trim(), tfSenha.getText(), ssl);
                iniManager.saveEmailConfig(newConfig);
                appConfig = iniManager.loadOrCreate();
                showSuccessAlert("Configurações de e-mail salvas com sucesso!");
            } catch (Exception ex) {
                log.error("Erro ao salvar config e-mail", ex);
                showAlert("Erro ao salvar: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });

        btnBox.getChildren().addAll(btnTest, btnSave);
        pane.getChildren().addAll(title, grid, modeBox, secNote, btnBox);
        contentArea.getChildren().setAll(new ScrollPane(pane) {{
            setFitToWidth(true);
            setStyle("-fx-background: transparent;");
        }});
    }

    private void showConfig() {
        VBox pane = new VBox(16);
        pane.setPadding(new Insets(30));
        pane.getStyleClass().add("content-pane");

        Label title = new Label("Configurações do Sistema");
        title.getStyleClass().add("page-title");

        // --- Caminhos XML ---
        TitledPane xmlPane = new TitledPane();
        xmlPane.setText("Caminhos dos Arquivos XML");
        VBox xmlBox = new VBox(10);
        xmlBox.setPadding(new Insets(12));

        TextField tfNfe = createDirField(appConfig.caminhoXmlNfe(), "Pasta NF-e", xmlBox);
        TextField tfNfce = createDirField(appConfig.caminhoXmlNfce(), "Pasta NFC-e", xmlBox);
        TextField tfComp = createDirField(appConfig.caminhoXmlCompras(), "Pasta Compras", xmlBox);

        Button btnSavePaths = new Button("💾 Salvar Caminhos");
        btnSavePaths.getStyleClass().add("btn-primary");
        btnSavePaths.setOnAction(e -> {
            try {
                iniManager.savePaths(tfNfe.getText(), tfNfce.getText(), tfComp.getText());
                appConfig = iniManager.loadOrCreate();
                showSuccessAlert("Caminhos salvos com sucesso!");
            } catch (Exception ex) {
                showAlert("Erro ao salvar caminhos: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });
        xmlBox.getChildren().add(btnSavePaths);
        xmlPane.setContent(xmlBox);
        xmlPane.setExpanded(true);

        // --- Conexão Firebird ---
        TitledPane dbPane = new TitledPane();
        dbPane.setText("Conexão Firebird");
        VBox dbBox = new VBox(10);
        dbBox.setPadding(new Insets(12));

        GridPane dbGrid = new GridPane();
        dbGrid.setHgap(14); dbGrid.setVgap(10);
        TextField tfIp = addField(dbGrid, "IP do Servidor:", appConfig.ipServidor(), 0);
        TextField tfPorta = addField(dbGrid, "Porta:", String.valueOf(appConfig.porta()), 1);
        TextField tfBase = addField(dbGrid, "Caminho do Banco:", appConfig.basePath(), 2);

        Button btnTestDb2 = new Button("🔌 Testar Conexão");
        btnTestDb2.getStyleClass().add("btn-secondary");
        btnTestDb2.setOnAction(e -> testDbConnection());

        dbBox.getChildren().addAll(dbGrid, btnTestDb2);
        dbPane.setContent(dbBox);

        // --- Segurança ---
        TitledPane secPane = new TitledPane();
        secPane.setText("Segurança de Acesso");
        VBox secBox = new VBox(10);
        secBox.setPadding(new Insets(12));

        CheckBox cbSenha = new CheckBox("Exigir senha ao abrir o programa");
        cbSenha.setSelected(appConfig.utilizaSenha());
        PasswordField pfSenha = new PasswordField();
        pfSenha.setText(appConfig.senhaAcesso());
        pfSenha.setPromptText("Nova senha de acesso");
        pfSenha.setDisable(!appConfig.utilizaSenha());
        cbSenha.setOnAction(e -> pfSenha.setDisable(!cbSenha.isSelected()));

        Button btnSaveSec = new Button("💾 Salvar Segurança");
        btnSaveSec.getStyleClass().add("btn-primary");
        btnSaveSec.setOnAction(e -> {
            try {
                iniManager.saveSenhaConfig(cbSenha.isSelected(), pfSenha.getText());
                appConfig = iniManager.loadOrCreate();
                showSuccessAlert("Configurações de segurança salvas!");
            } catch (Exception ex) {
                showAlert("Erro: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });

        secBox.getChildren().addAll(cbSenha, pfSenha, btnSaveSec);
        secPane.setContent(secBox);

        // --- Startup Windows ---
        TitledPane startupPane = new TitledPane();
        startupPane.setText("Inicialização Automática com Windows");
        VBox startupBox = new VBox(10);
        startupBox.setPadding(new Insets(12));

        Label startupInfo = new Label("Registrar o Módulo Fiscal para iniciar automaticamente com o Windows, minimizado na bandeja.");
        startupInfo.setWrapText(true);
        startupInfo.getStyleClass().add("note-label");

        HBox startupBtns = new HBox(10);
        Button btnReg = new Button("✅ Registrar no Startup");
        btnReg.getStyleClass().add("btn-success");
        btnReg.setOnAction(e -> {
            br.com.infoativa.fiscal.service.StartupService.registerStartup();
            showSuccessAlert("Módulo Fiscal registrado no startup do Windows!");
        });

        Button btnUnreg = new Button("❌ Remover do Startup");
        btnUnreg.getStyleClass().add("btn-danger");
        btnUnreg.setOnAction(e -> {
            br.com.infoativa.fiscal.service.StartupService.unregisterStartup();
            showSuccessAlert("Registro de startup removido.");
        });
        startupBtns.getChildren().addAll(btnReg, btnUnreg);
        startupBox.getChildren().addAll(startupInfo, startupBtns);
        startupPane.setContent(startupBox);

        pane.getChildren().addAll(title, xmlPane, dbPane, secPane, startupPane);
        contentArea.getChildren().setAll(new ScrollPane(pane) {{
            setFitToWidth(true);
            setStyle("-fx-background: transparent;");
        }});
    }

    // ── Helpers UI ────────────────────────────────────────────────────────────

    private VBox buildInfoCard(String label, String value) {
        VBox card = new VBox(6);
        card.getStyleClass().add("info-card");
        card.setPadding(new Insets(16));
        card.setPrefWidth(200);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-label");
        Label val = new Label(value);
        val.getStyleClass().add("card-value");
        val.setWrapText(true);
        card.getChildren().addAll(lbl, val);
        return card;
    }

    private TextField addField(GridPane grid, String label, String value, int row) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("field-label");
        TextField tf = new TextField(value != null ? value : "");
        tf.getStyleClass().add("text-field-fiscal");
        GridPane.setHgrow(tf, Priority.ALWAYS);
        grid.add(lbl, 0, row);
        grid.add(tf, 1, row);
        return tf;
    }

    private TextField createDirField(String value, String placeholder, VBox parent) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        TextField tf = new TextField(value != null ? value : "");
        tf.setPromptText(placeholder);
        tf.getStyleClass().add("text-field-fiscal");
        HBox.setHgrow(tf, Priority.ALWAYS);
        Button browse = new Button("📁");
        browse.getStyleClass().add("btn-outline");
        browse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Selecionar pasta – " + placeholder);
            File selected = dc.showDialog(primaryStage);
            if (selected != null) tf.setText(selected.getAbsolutePath());
        });
        row.getChildren().addAll(tf, browse);
        parent.getChildren().add(row);
        return tf;
    }

    private void appendLog(TextArea ta, String msg) {
        Platform.runLater(() -> {
            ta.appendText(msg + "\n");
            ta.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void testDbConnection() {
        boolean ok = FirebirdConnectionFactory.testConnection(appConfig);
        if (ok) showSuccessAlert("✅ Conexão com o banco de dados realizada com sucesso!");
        else showAlert("❌ Não foi possível conectar ao banco de dados.\nVerifique IP, porta e caminho do banco.", Alert.AlertType.ERROR);
    }

    private void ensureDbConnected() throws Exception {
        if (dbGateway == null || dbGateway.getConnection().isClosed()) {
            dbGateway = DatabaseGateway.open(appConfig);
            tryLoadCompanyInfo();
        }
    }

    private void runInBackground(Runnable task) {
        bgExecutor.submit(task);
    }

    private void showAlert(String msg, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, msg, ButtonType.OK);
            alert.setTitle("Módulo Fiscal");
            alert.initOwner(primaryStage);
            alert.showAndWait();
        });
    }

    private void showSuccessAlert(String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
            alert.setTitle("✅ Sucesso");
            alert.initOwner(primaryStage);
            alert.showAndWait();
        });
    }

    private void confirmExit(Stage stage) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
            "Deseja fechar o Módulo Fiscal?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Fechar");
        alert.initOwner(stage);
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                shutdown();
                Platform.exit();
            }
        });
    }

    private void shutdown() {
        bgExecutor.shutdownNow();
        SystemTrayManager.getInstance().shutdown();
        if (dbGateway != null) {
            try { dbGateway.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void loadStylesheet(Scene scene) {
        try {
            var resource = getClass().getResource("/styles/main.css");
            if (resource != null) scene.getStylesheets().add(resource.toExternalForm());
        } catch (Exception e) {
            log.warn("CSS não encontrado: {}", e.getMessage());
        }
    }

    private AppConfig defaultConfig() {
        return new AppConfig("127.0.0.1", 3050, "C:\\TSD\\Host\\HOST.FDB",
            "C:\\TSD\\Host\\XML", "C:\\TSD\\Host\\XML_NFCe",
            "C:\\TSD\\Host\\XML_Fornecedores", false, "",
            false, new EmailConfig("mail.infoativa.com.br", 465,
                "fiscal@infoativa.com.br", "", false),
            new java.util.ArrayList<>());
    }

    // ── Ícones SVG ────────────────────────────────────────────────────────────

    private static final String ICON_HOME     = "M3,13h8V3H3V13z M3,21h8v-6H3V21z M13,21h8V11h-8V21z M13,3v6h8V3H13z";
    private static final String ICON_REPORT   = "M19,3H5C3.9,3,3,3.9,3,5v14c0,1.1,0.9,2,2,2h14c1.1,0,2-0.9,2-2V5C21,3.9,20.1,3,19,3z M9,17H7v-7h2V17z M13,17h-2V7h2V17z M17,17h-2v-4h2V17z";
    private static final String ICON_REFRESH  = "M17.65,6.35C16.2,4.9,14.21,4,12,4A8,8,0,1,0,20,12h-2A6,6,0,1,1,12,6a5.92,5.92,0,0,1,4.22,1.78L13,11h7V4Z";
    private static final String ICON_EMAIL    = "M20,4H4C2.9,4,2,4.9,2,6v12c0,1.1,0.9,2,2,2h16c1.1,0,2-0.9,2-2V6C22,4.9,21.1,4,20,4z M20,8l-8,5L4,8V6l8,5l8-5V8z";
    private static final String ICON_SHIELD   = "M12,1L3,5v6c0,5.55,3.84,10.74,9,12c5.16-1.26,9-6.45,9-12V5L12,1z";
    private static final String ICON_SETTINGS = "M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.07,0.94l-2.03,1.58c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.43-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z";
}
