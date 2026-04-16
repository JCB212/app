package br.com.infoativa.fiscal.ui;

import br.com.infoativa.fiscal.config.AppConfig;
import br.com.infoativa.fiscal.config.IniManager;
import br.com.infoativa.fiscal.db.DatabaseGateway;
import br.com.infoativa.fiscal.db.FirebirdConnectionFactory;
import br.com.infoativa.fiscal.domain.EmailConfig;
import br.com.infoativa.fiscal.domain.Periodo;
import br.com.infoativa.fiscal.domain.ProcessamentoResult;
import br.com.infoativa.fiscal.mail.EmailService;
import br.com.infoativa.fiscal.service.ClosingOrchestrator;
import br.com.infoativa.fiscal.service.PeriodService;
import br.com.infoativa.fiscal.service.ProcessingMode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
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

public class FiscalApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(FiscalApp.class);
    private AppConfig appConfig;
    private IniManager iniManager;
    private DatabaseGateway dbGateway;
    private StackPane contentArea;
    private VBox sidebarMenu;
    private Label statusLabel;
    private String activeMenu = "home";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        iniManager = new IniManager();
        try {
            appConfig = iniManager.loadOrCreate();
        } catch (IOException e) {
            showError("Erro ao carregar configuracao", e.getMessage());
            appConfig = new AppConfig("127.0.0.1", 3050, "", "C:\\TSD\\Host\\XML",
                "C:\\TSD\\Host\\XML_NFCe", "C:\\TSD\\Host\\XML_Fornecedores",
                false, "", false,
                new EmailConfig("mail.infoativa.com.br", 465, "fiscal@infoativa.com.br", "Info2024@#--", true),
                List.of("jean.carlos@infoativa.com.br"));
        }

        // Check password if required
        if (appConfig.utilizaSenha()) {
            if (!showPasswordDialog(stage)) {
                Platform.exit();
                return;
            }
        }

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");

        // Sidebar
        VBox sidebar = createSidebar();
        root.setLeft(sidebar);

        // Content area
        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        contentArea.setPadding(new Insets(0));
        root.setCenter(contentArea);

        // Status bar
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        showHome();

        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm());

        stage.setTitle("Modulo Fiscal - InfoAtiva");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(0);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(260);

        // Logo area
        VBox logoBox = new VBox(4);
        logoBox.getStyleClass().add("sidebar-logo");
        logoBox.setPadding(new Insets(28, 20, 20, 20));
        logoBox.setAlignment(Pos.CENTER_LEFT);

        Label logo = new Label("INFOATIVA");
        logo.getStyleClass().add("logo-text");
        Label subtitle = new Label("Modulo Fiscal");
        subtitle.getStyleClass().add("logo-subtitle");
        Label version = new Label("v1.0.0");
        version.getStyleClass().add("version-label");
        logoBox.getChildren().addAll(logo, subtitle, version);

        // Separator
        Region sep = new Region();
        sep.getStyleClass().add("sidebar-separator");
        sep.setPrefHeight(1);

        // Menu items
        sidebarMenu = new VBox(2);
        sidebarMenu.setPadding(new Insets(16, 12, 16, 12));

        Button btnHome = createMenuButton("Inicio", "M3,13h8V3H3V13z M3,21h8v-6H3V21z M13,21h8V11h-8V21z M13,3v6h8V3H13z", "home");
        Button btnProcess = createMenuButton("Gerar e Enviar", "M19,3H5C3.9,3,3,3.9,3,5v14c0,1.1,0.9,2,2,2h14c1.1,0,2-0.9,2-2V5C21,3.9,20.1,3,19,3z M9,17H7v-7h2V17z M13,17h-2V7h2V17z M17,17h-2v-4h2V17z", "process");
        Button btnDest = createMenuButton("Destinatarios", "M20,4H4C2.9,4,2,4.9,2,6v12c0,1.1,0.9,2,2,2h16c1.1,0,2-0.9,2-2V6C22,4.9,21.1,4,20,4z M20,8l-8,5L4,8V6l8,5l8-5V8z", "dest");
        Button btnEmail = createMenuButton("Config. Email", "M12,1L3,5v6c0,5.55,3.84,10.74,9,12c5.16-1.26,9-6.45,9-12V5L12,1z M12,11.99h7c-0.53,4.12-3.28,7.79-7,8.94V12H5V6.3l7-3.11v8.8z", "email");
        Button btnConfig = createMenuButton("Configuracoes", "M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.07,0.94l-2.03,1.58c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.43-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z", "config");

        sidebarMenu.getChildren().addAll(btnHome, btnProcess, btnDest, btnEmail, btnConfig);

        // DB status
        VBox dbStatus = new VBox(4);
        dbStatus.setPadding(new Insets(16, 20, 20, 20));
        dbStatus.setAlignment(Pos.BOTTOM_LEFT);
        VBox.setVgrow(dbStatus, Priority.ALWAYS);

        Label dbLabel = new Label("Banco de Dados");
        dbLabel.getStyleClass().add("db-status-title");
        Label dbInfo = new Label(appConfig.ipServidor() + ":" + appConfig.porta());
        dbInfo.getStyleClass().add("db-status-info");
        Button btnTestDb = new Button("Testar Conexao");
        btnTestDb.getStyleClass().addAll("btn-small", "btn-outline");
        btnTestDb.setOnAction(e -> testDbConnection());
        dbStatus.getChildren().addAll(dbLabel, dbInfo, btnTestDb);

        sidebar.getChildren().addAll(logoBox, sep, sidebarMenu, dbStatus);
        return sidebar;
    }

    private Button createMenuButton(String text, String svgPath, String id) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setFill(Color.web("#94a3b8"));
        icon.setScaleX(0.75);
        icon.setScaleY(0.75);

        Label label = new Label(text);
        label.getStyleClass().add("menu-label");

        HBox content = new HBox(12);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(icon, label);

        Button btn = new Button();
        btn.setGraphic(content);
        btn.getStyleClass().add("sidebar-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setUserData(id);

        btn.setOnAction(e -> {
            activeMenu = id;
            updateMenuHighlight();
            switch (id) {
                case "home" -> showHome();
                case "process" -> showProcessing();
                case "dest" -> showDestinatarios();
                case "email" -> showEmailConfig();
                case "config" -> showConfig();
            }
        });

        return btn;
    }

    private void updateMenuHighlight() {
        for (var node : sidebarMenu.getChildren()) {
            if (node instanceof Button btn) {
                btn.getStyleClass().remove("sidebar-btn-active");
                if (activeMenu.equals(btn.getUserData())) {
                    btn.getStyleClass().add("sidebar-btn-active");
                }
            }
        }
    }

    private HBox createStatusBar() {
        HBox bar = new HBox(16);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(8, 20, 8, 20));
        bar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Pronto");
        statusLabel.getStyleClass().add("status-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label clock = new Label(LocalDate.now().toString());
        clock.getStyleClass().add("status-text");

        bar.getChildren().addAll(statusLabel, spacer, clock);
        return bar;
    }

    // ====================== HOME VIEW ======================
    private void showHome() {
        VBox home = new VBox(24);
        home.setPadding(new Insets(32));
        home.getStyleClass().add("view-container");

        Label title = new Label("Painel Principal");
        title.getStyleClass().add("view-title");

        Label subtitle = new Label("Modulo de Fechamento Fiscal Automatico");
        subtitle.getStyleClass().add("view-subtitle");

        // Quick action cards
        HBox cards = new HBox(20);
        cards.setAlignment(Pos.CENTER_LEFT);

        Periodo autoP = PeriodService.resolveAuto();
        VBox card1 = createInfoCard("Periodo Atual",
            PeriodService.nomeMes(autoP.inicio().getMonthValue()) + "/" + autoP.inicio().getYear(),
            "Periodo automatico para fechamento", "#3b82f6");
        VBox card2 = createInfoCard("Servidor",
            appConfig.ipServidor() + ":" + appConfig.porta(), "Banco de dados Firebird", "#8b5cf6");
        VBox card3 = createInfoCard("Destinatarios",
            appConfig.emailDestinatarios().size() + " configurado(s)",
            "Emails do contador", "#10b981");

        cards.getChildren().addAll(card1, card2, card3);

        // Quick actions
        Label actionsTitle = new Label("Acoes Rapidas");
        actionsTitle.getStyleClass().add("section-title");

        HBox actions = new HBox(16);
        Button btnQuickProcess = createActionButton("Processar Mes Anterior", "#3b82f6");
        btnQuickProcess.setOnAction(e -> {
            showProcessing();
            activeMenu = "process";
            updateMenuHighlight();
        });
        Button btnQuickAnual = createActionButton("Gerar Anual", "#8b5cf6");
        btnQuickAnual.setOnAction(e -> {
            showProcessing();
            activeMenu = "process";
            updateMenuHighlight();
        });
        actions.getChildren().addAll(btnQuickProcess, btnQuickAnual);

        // Info
        VBox infoBox = new VBox(8);
        infoBox.getStyleClass().add("info-box");
        infoBox.setPadding(new Insets(16));
        Label infoTitle = new Label("Como funciona");
        infoTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label infoText = new Label(
            "1. Configure os caminhos dos XMLs e email em Configuracoes\n" +
            "2. Adicione os emails dos contadores em Destinatarios\n" +
            "3. Use 'Gerar e Enviar' para processar o fechamento fiscal\n" +
            "4. O sistema gera SPED Fiscal, SPED Contribuicoes, SINTEGRA e PDFs\n" +
            "5. Tudo e compactado em ZIP e enviado automaticamente por email"
        );
        infoText.setWrapText(true);
        infoText.getStyleClass().add("info-text");
        infoBox.getChildren().addAll(infoTitle, infoText);

        home.getChildren().addAll(title, subtitle, cards, actionsTitle, actions, infoBox);

        ScrollPane scroll = new ScrollPane(home);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        contentArea.getChildren().setAll(scroll);
        updateMenuHighlight();
    }

    private VBox createInfoCard(String title, String value, String desc, String color) {
        VBox card = new VBox(8);
        card.getStyleClass().add("info-card");
        card.setPadding(new Insets(20));
        card.setPrefWidth(220);
        card.setStyle("-fx-border-color: " + color + "; -fx-border-width: 0 0 0 4px;");

        Label tl = new Label(title);
        tl.getStyleClass().add("card-title");
        Label vl = new Label(value);
        vl.getStyleClass().add("card-value");
        vl.setStyle("-fx-text-fill: " + color + ";");
        Label dl = new Label(desc);
        dl.getStyleClass().add("card-desc");
        dl.setWrapText(true);

        card.getChildren().addAll(tl, vl, dl);
        return card;
    }

    private Button createActionButton(String text, String color) {
        Button btn = new Button(text);
        btn.getStyleClass().add("action-btn");
        btn.setStyle("-fx-background-color: " + color + ";");
        return btn;
    }

    // ====================== PROCESSING VIEW ======================
    private void showProcessing() {
        VBox view = new VBox(20);
        view.setPadding(new Insets(32));
        view.getStyleClass().add("view-container");

        Label title = new Label("Geracao de Arquivos para Contabilidade");
        title.getStyleClass().add("view-title");

        // Mode selection
        HBox modeBox = new HBox(16);
        modeBox.setAlignment(Pos.CENTER_LEFT);
        Label modeLabel = new Label("Modo:");
        modeLabel.getStyleClass().add("field-label");
        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton rbAuto = new RadioButton("Automatico (Mes Anterior)");
        rbAuto.setToggleGroup(modeGroup);
        rbAuto.setSelected(true);
        rbAuto.getStyleClass().add("radio-custom");
        RadioButton rbManual = new RadioButton("Manual");
        rbManual.setToggleGroup(modeGroup);
        rbManual.getStyleClass().add("radio-custom");
        RadioButton rbAnual = new RadioButton("Anual");
        rbAnual.setToggleGroup(modeGroup);
        rbAnual.getStyleClass().add("radio-custom");
        modeBox.getChildren().addAll(modeLabel, rbAuto, rbManual, rbAnual);

        // Period selection
        HBox periodBox = new HBox(16);
        periodBox.setAlignment(Pos.CENTER_LEFT);

        Label periodoLabel = new Label("Periodo:");
        periodoLabel.getStyleClass().add("field-label");
        ComboBox<String> cbMes = new ComboBox<>(FXCollections.observableArrayList(
            "Janeiro","Fevereiro","Marco","Abril","Maio","Junho",
            "Julho","Agosto","Setembro","Outubro","Novembro","Dezembro"
        ));
        cbMes.getStyleClass().add("combo-custom");
        cbMes.setValue(PeriodService.nomeMes(LocalDate.now().minusMonths(1).getMonthValue()));

        Label anoLabel = new Label("Ano:");
        anoLabel.getStyleClass().add("field-label");
        TextField tfAno = new TextField(String.valueOf(LocalDate.now().getYear()));
        tfAno.getStyleClass().add("text-field-custom");
        tfAno.setPrefWidth(80);

        periodBox.getChildren().addAll(periodoLabel, cbMes, anoLabel, tfAno);

        // Checkboxes for document types
        Label tipoLabel = new Label("Tipos de Documento:");
        tipoLabel.getStyleClass().add("field-label");
        HBox checkBoxes = new HBox(16);
        CheckBox chkNfce = new CheckBox("Vendas NFCe");
        chkNfce.setSelected(true);
        CheckBox chkNfe = new CheckBox("Vendas NFe");
        chkNfe.setSelected(true);
        CheckBox chkCompras = new CheckBox("Notas de Compra");
        chkCompras.setSelected(true);
        CheckBox chkSped = new CheckBox("SPED Fiscal");
        chkSped.setSelected(true);
        CheckBox chkContrib = new CheckBox("SPED Contribuicoes");
        chkContrib.setSelected(true);
        CheckBox chkSintegra = new CheckBox("SINTEGRA");
        chkSintegra.setSelected(true);
        checkBoxes.getChildren().addAll(chkNfce, chkNfe, chkCompras);
        HBox checkBoxes2 = new HBox(16);
        checkBoxes2.getChildren().addAll(chkSped, chkContrib, chkSintegra);

        // Buttons
        HBox btnBox = new HBox(16);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        CheckBox chkEnviarEmail = new CheckBox("Enviar por email apos processar");
        chkEnviarEmail.setSelected(true);
        chkEnviarEmail.getStyleClass().add("check-accent");

        Button btnProcessar = new Button("Processar");
        btnProcessar.getStyleClass().addAll("action-btn", "btn-primary");
        btnProcessar.setStyle("-fx-background-color: #3b82f6;");

        btnBox.getChildren().addAll(chkEnviarEmail, btnProcessar);

        // Progress and log area
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("progress-custom");
        progressBar.setMaxWidth(Double.MAX_VALUE);

        TextArea logArea = new TextArea();
        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false);
        logArea.setPrefRowCount(12);
        logArea.setWrapText(true);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // Process button action
        btnProcessar.setOnAction(e -> {
            logArea.clear();
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            btnProcessar.setDisable(true);
            statusLabel.setText("Processando...");

            new Thread(() -> {
                try {
                    FirebirdConnectionFactory factory = new FirebirdConnectionFactory(
                        appConfig.ipServidor(), appConfig.porta(), appConfig.basePath());
                    DatabaseGateway gateway = new DatabaseGateway(factory);
                    ClosingOrchestrator orchestrator = new ClosingOrchestrator(appConfig, gateway);

                    boolean isAnual = rbAnual.isSelected();
                    boolean enviar = chkEnviarEmail.isSelected();

                    if (isAnual) {
                        int ano = Integer.parseInt(tfAno.getText().trim());
                        orchestrator.executeAnual(ano, enviar, msg ->
                            Platform.runLater(() -> logArea.appendText(msg + "\n")));
                    } else {
                        Periodo periodo;
                        if (rbAuto.isSelected()) {
                            periodo = PeriodService.resolveAuto();
                        } else {
                            int mes = cbMes.getSelectionModel().getSelectedIndex() + 1;
                            int ano = Integer.parseInt(tfAno.getText().trim());
                            YearMonth ym = YearMonth.of(ano, mes);
                            periodo = new Periodo(ym.atDay(1), ym.atEndOfMonth());
                        }
                        orchestrator.execute(periodo, enviar, msg ->
                            Platform.runLater(() -> logArea.appendText(msg + "\n")));
                    }

                    Platform.runLater(() -> {
                        progressBar.setProgress(1);
                        btnProcessar.setDisable(false);
                        statusLabel.setText("Processamento concluido!");
                    });
                    gateway.close();
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        logArea.appendText("ERRO: " + ex.getMessage() + "\n");
                        progressBar.setProgress(0);
                        btnProcessar.setDisable(false);
                        statusLabel.setText("Erro no processamento");
                    });
                }
            }).start();
        });

        view.getChildren().addAll(title, modeBox, periodBox, tipoLabel, checkBoxes, checkBoxes2,
            btnBox, progressBar, logArea);

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        contentArea.getChildren().setAll(scroll);
    }

    // ====================== DESTINATARIOS VIEW ======================
    private void showDestinatarios() {
        VBox view = new VBox(20);
        view.setPadding(new Insets(32));
        view.getStyleClass().add("view-container");

        Label title = new Label("Gerenciamento de Destinatarios");
        title.getStyleClass().add("view-title");
        Label subtitle = new Label("Emails dos contadores que receberao o fechamento fiscal");
        subtitle.getStyleClass().add("view-subtitle");

        ObservableList<String> emails = FXCollections.observableArrayList(
            new ArrayList<>(appConfig.emailDestinatarios()));

        ListView<String> listView = new ListView<>(emails);
        listView.getStyleClass().add("list-custom");
        listView.setPrefHeight(300);

        HBox addBox = new HBox(12);
        addBox.setAlignment(Pos.CENTER_LEFT);
        TextField tfEmail = new TextField();
        tfEmail.getStyleClass().add("text-field-custom");
        tfEmail.setPromptText("email@exemplo.com.br");
        tfEmail.setPrefWidth(350);
        HBox.setHgrow(tfEmail, Priority.ALWAYS);

        Button btnAdd = new Button("Adicionar");
        btnAdd.getStyleClass().addAll("action-btn");
        btnAdd.setStyle("-fx-background-color: #10b981;");

        Button btnRemove = new Button("Remover Selecionado");
        btnRemove.getStyleClass().addAll("action-btn");
        btnRemove.setStyle("-fx-background-color: #ef4444;");

        addBox.getChildren().addAll(tfEmail, btnAdd, btnRemove);

        Button btnSave = new Button("Salvar Destinatarios");
        btnSave.getStyleClass().addAll("action-btn", "btn-primary");
        btnSave.setStyle("-fx-background-color: #3b82f6;");

        btnAdd.setOnAction(e -> {
            String email = tfEmail.getText().trim();
            if (!email.isEmpty() && email.contains("@")) {
                emails.add(email);
                tfEmail.clear();
            }
        });

        btnRemove.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) emails.remove(sel);
        });

        btnSave.setOnAction(e -> {
            try {
                iniManager.saveDestinatarios(new ArrayList<>(emails));
                appConfig = iniManager.loadOrCreate();
                showAlert(Alert.AlertType.INFORMATION, "Salvo", "Destinatarios salvos com sucesso!");
            } catch (IOException ex) {
                showError("Erro", ex.getMessage());
            }
        });

        view.getChildren().addAll(title, subtitle, addBox, listView, btnSave);
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        contentArea.getChildren().setAll(scroll);
    }

    // ====================== EMAIL CONFIG VIEW ======================
    private void showEmailConfig() {
        VBox view = new VBox(20);
        view.setPadding(new Insets(32));
        view.getStyleClass().add("view-container");

        Label title = new Label("Configuracoes de Envio de Email");
        title.getStyleClass().add("view-title");

        // Form fields
        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(12);
        form.getStyleClass().add("form-grid");

        Label lblHost = new Label("Servidor SMTP:");
        lblHost.getStyleClass().add("field-label");
        TextField tfHost = new TextField(appConfig.emailConfig().host());
        tfHost.getStyleClass().add("text-field-custom");

        Label lblPort = new Label("Porta:");
        lblPort.getStyleClass().add("field-label");
        TextField tfPort = new TextField(String.valueOf(appConfig.emailConfig().port()));
        tfPort.getStyleClass().add("text-field-custom");
        tfPort.setPrefWidth(80);

        Label lblUser = new Label("Email:");
        lblUser.getStyleClass().add("field-label");
        TextField tfUser = new TextField(appConfig.emailConfig().usuario());
        tfUser.getStyleClass().add("text-field-custom");

        Label lblPass = new Label("Senha:");
        lblPass.getStyleClass().add("field-label");
        PasswordField tfPass = new PasswordField();
        tfPass.getStyleClass().add("text-field-custom");
        tfPass.setText(appConfig.emailConfig().senha());

        CheckBox chkSsl = new CheckBox("Autenticacao Segura (SSL)");
        chkSsl.setSelected(appConfig.emailConfig().ssl());

        form.add(lblHost, 0, 0); form.add(tfHost, 1, 0, 2, 1);
        form.add(lblPort, 3, 0); form.add(tfPort, 4, 0);
        form.add(lblUser, 0, 1); form.add(tfUser, 1, 1, 4, 1);
        form.add(lblPass, 0, 2); form.add(tfPass, 1, 2, 2, 1);
        form.add(chkSsl, 3, 2, 2, 1);

        // Quick presets
        Label presetsLabel = new Label("Configuracoes Rapidas:");
        presetsLabel.getStyleClass().add("section-title");

        HBox presets = new HBox(12);
        presets.setAlignment(Pos.CENTER_LEFT);
        String[][] presetData = {
            {"Gmail", "smtp.gmail.com", "587"},
            {"Hotmail", "smtp.live.com", "587"},
            {"Yahoo", "smtp.mail.yahoo.com", "587"},
            {"Terra", "smtp.terra.com.br", "587"},
            {"UOL", "smtps.uol.com.br", "587"},
            {"InfoAtiva", "mail.infoativa.com.br", "465"}
        };
        for (String[] p : presetData) {
            Button btn = new Button(p[0]);
            btn.getStyleClass().addAll("btn-small", "btn-preset");
            btn.setOnAction(e -> {
                tfHost.setText(p[1]);
                tfPort.setText(p[2]);
                chkSsl.setSelected("465".equals(p[2]));
            });
            presets.getChildren().add(btn);
        }

        // Action buttons
        HBox actions = new HBox(16);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button btnTest = new Button("Testar Conexao");
        btnTest.getStyleClass().addAll("action-btn");
        btnTest.setStyle("-fx-background-color: #f59e0b;");

        Button btnSave = new Button("Salvar");
        btnSave.getStyleClass().addAll("action-btn", "btn-primary");
        btnSave.setStyle("-fx-background-color: #3b82f6;");

        actions.getChildren().addAll(btnTest, btnSave);

        Label resultLabel = new Label();
        resultLabel.getStyleClass().add("result-label");

        btnTest.setOnAction(e -> {
            EmailConfig testConfig = new EmailConfig(
                tfHost.getText().trim(), Integer.parseInt(tfPort.getText().trim()),
                tfUser.getText().trim(), tfPass.getText(), chkSsl.isSelected());
            btnTest.setDisable(true);
            resultLabel.setText("Testando...");
            new Thread(() -> {
                boolean ok = EmailService.testConnection(testConfig);
                Platform.runLater(() -> {
                    resultLabel.setText(ok ? "Conexao OK!" : "Falha na conexao");
                    resultLabel.setStyle(ok ? "-fx-text-fill: #10b981;" : "-fx-text-fill: #ef4444;");
                    btnTest.setDisable(false);
                });
            }).start();
        });

        btnSave.setOnAction(e -> {
            try {
                EmailConfig newConfig = new EmailConfig(
                    tfHost.getText().trim(), Integer.parseInt(tfPort.getText().trim()),
                    tfUser.getText().trim(), tfPass.getText(), chkSsl.isSelected());
                iniManager.saveEmailConfig(newConfig);
                appConfig = iniManager.loadOrCreate();
                showAlert(Alert.AlertType.INFORMATION, "Salvo", "Configuracoes de email salvas!");
            } catch (Exception ex) {
                showError("Erro", ex.getMessage());
            }
        });

        view.getChildren().addAll(title, form, presetsLabel, presets, actions, resultLabel);
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        contentArea.getChildren().setAll(scroll);
    }

    // ====================== CONFIG VIEW ======================
    private void showConfig() {
        VBox view = new VBox(20);
        view.setPadding(new Insets(32));
        view.getStyleClass().add("view-container");

        Label title = new Label("Configuracoes Gerais");
        title.getStyleClass().add("view-title");

        // XML Paths
        Label pathsTitle = new Label("Caminhos dos XMLs");
        pathsTitle.getStyleClass().add("section-title");

        GridPane pathGrid = new GridPane();
        pathGrid.setHgap(12);
        pathGrid.setVgap(12);

        Label lblNfe = new Label("XML NFe:");
        lblNfe.getStyleClass().add("field-label");
        TextField tfNfe = new TextField(appConfig.caminhoXmlNfe());
        tfNfe.getStyleClass().add("text-field-custom");
        tfNfe.setPrefWidth(400);
        Button btnBrowseNfe = new Button("...");
        btnBrowseNfe.getStyleClass().add("btn-small");
        btnBrowseNfe.setOnAction(e -> browseDir(tfNfe));

        Label lblNfce = new Label("XML NFCe:");
        lblNfce.getStyleClass().add("field-label");
        TextField tfNfce = new TextField(appConfig.caminhoXmlNfce());
        tfNfce.getStyleClass().add("text-field-custom");
        tfNfce.setPrefWidth(400);
        Button btnBrowseNfce = new Button("...");
        btnBrowseNfce.getStyleClass().add("btn-small");
        btnBrowseNfce.setOnAction(e -> browseDir(tfNfce));

        Label lblCompras = new Label("XML Compras:");
        lblCompras.getStyleClass().add("field-label");
        TextField tfCompras = new TextField(appConfig.caminhoXmlCompras());
        tfCompras.getStyleClass().add("text-field-custom");
        tfCompras.setPrefWidth(400);
        Button btnBrowseCompras = new Button("...");
        btnBrowseCompras.getStyleClass().add("btn-small");
        btnBrowseCompras.setOnAction(e -> browseDir(tfCompras));

        pathGrid.add(lblNfe, 0, 0); pathGrid.add(tfNfe, 1, 0); pathGrid.add(btnBrowseNfe, 2, 0);
        pathGrid.add(lblNfce, 0, 1); pathGrid.add(tfNfce, 1, 1); pathGrid.add(btnBrowseNfce, 2, 1);
        pathGrid.add(lblCompras, 0, 2); pathGrid.add(tfCompras, 1, 2); pathGrid.add(btnBrowseCompras, 2, 2);

        // Security
        Label secTitle = new Label("Seguranca");
        secTitle.getStyleClass().add("section-title");

        HBox secBox = new HBox(16);
        secBox.setAlignment(Pos.CENTER_LEFT);
        CheckBox chkSenha = new CheckBox("Exigir senha de acesso");
        chkSenha.setSelected(appConfig.utilizaSenha());
        TextField tfSenha = new TextField(appConfig.senhaAcesso());
        tfSenha.getStyleClass().add("text-field-custom");
        tfSenha.setPromptText("Senha de acesso");
        tfSenha.setPrefWidth(200);
        secBox.getChildren().addAll(chkSenha, tfSenha);

        // DB Info
        Label dbTitle = new Label("Banco de Dados (conexao.ini)");
        dbTitle.getStyleClass().add("section-title");
        Label dbInfo = new Label("IP: " + appConfig.ipServidor() + "  |  Porta: " + appConfig.porta()
            + "  |  Base: " + appConfig.basePath());
        dbInfo.getStyleClass().add("info-text");

        // Save button
        Button btnSave = new Button("Salvar Configuracoes");
        btnSave.getStyleClass().addAll("action-btn", "btn-primary");
        btnSave.setStyle("-fx-background-color: #3b82f6;");

        btnSave.setOnAction(e -> {
            try {
                iniManager.savePaths(tfNfe.getText().trim(), tfNfce.getText().trim(), tfCompras.getText().trim());
                iniManager.saveSenhaConfig(chkSenha.isSelected(), tfSenha.getText().trim());
                appConfig = iniManager.loadOrCreate();
                showAlert(Alert.AlertType.INFORMATION, "Salvo", "Configuracoes salvas com sucesso!");
            } catch (IOException ex) {
                showError("Erro", ex.getMessage());
            }
        });

        view.getChildren().addAll(title, pathsTitle, pathGrid, secTitle, secBox, dbTitle, dbInfo, btnSave);
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        contentArea.getChildren().setAll(scroll);
    }

    // ====================== UTILITIES ======================

    private void browseDir(TextField tf) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Selecionar Pasta");
        if (!tf.getText().isEmpty()) {
            File dir = new File(tf.getText());
            if (dir.exists()) dc.setInitialDirectory(dir);
        }
        File selected = dc.showDialog(contentArea.getScene().getWindow());
        if (selected != null) tf.setText(selected.getAbsolutePath());
    }

    private void testDbConnection() {
        statusLabel.setText("Testando conexao...");
        new Thread(() -> {
            try {
                FirebirdConnectionFactory factory = new FirebirdConnectionFactory(
                    appConfig.ipServidor(), appConfig.porta(), appConfig.basePath());
                DatabaseGateway gw = new DatabaseGateway(factory);
                boolean ok = gw.testConnection();
                gw.close();
                Platform.runLater(() -> {
                    if (ok) {
                        showAlert(Alert.AlertType.INFORMATION, "Conexao", "Conexao com Firebird OK!");
                        statusLabel.setText("Banco de dados conectado");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Erro", "Nao foi possivel conectar ao banco");
                        statusLabel.setText("Falha na conexao");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Erro de Conexao", e.getMessage());
                    statusLabel.setText("Erro: " + e.getMessage());
                });
            }
        }).start();
    }

    private boolean showPasswordDialog(Stage owner) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Acesso ao Sistema");
        dialog.setHeaderText("Modulo Fiscal - Acesso Restrito");
        dialog.setContentText("Senha:");
        dialog.initOwner(owner);

        var result = dialog.showAndWait();
        if (result.isPresent()) {
            return result.get().equals(appConfig.senhaAcesso());
        }
        return false;
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showError(String title, String msg) {
        showAlert(Alert.AlertType.ERROR, title, msg);
    }
}
