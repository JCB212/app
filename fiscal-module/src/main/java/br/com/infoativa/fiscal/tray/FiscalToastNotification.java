package br.com.infoativa.fiscal.tray;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.*;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Toast notification profissional no canto inferior esquerdo.
 * Exibido por N segundos com opção de fechar manualmente.
 * Design moderno, sombra e animação de entrada/saída.
 */
public class FiscalToastNotification {

    private static final Logger log = LoggerFactory.getLogger(FiscalToastNotification.class);

    private final String title;
    private final String message;
    private final int durationSeconds;
    private Stage stage;

    public FiscalToastNotification(String title, String message, int durationSeconds) {
        this.title = title;
        this.message = message;
        this.durationSeconds = durationSeconds;
    }

    public void show() {
        Platform.runLater(this::buildAndShow);
    }

    private void buildAndShow() {
        stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.initModality(Modality.NONE);
        stage.setResizable(false);

        // Container principal
        VBox root = new VBox(12);
        root.setPadding(new Insets(18, 22, 18, 22));
        root.setStyle(
            "-fx-background-color: rgba(255,255,255,0.97);" +
            "-fx-background-radius: 12px;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.35), 18, 0.1, 0, 4);" +
            "-fx-border-color: #22c55e;" +
            "-fx-border-width: 0 0 0 5px;" +
            "-fx-border-radius: 12px;" +
            "-fx-min-width: 340px;" +
            "-fx-max-width: 380px;"
        );

        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label("✅");
        iconLabel.setStyle("-fx-font-size: 22px;");

        VBox textBox = new VBox(4);
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        titleLabel.setStyle("-fx-text-fill: #15803d;");

        Label msgLabel = new Label(message);
        msgLabel.setFont(Font.font("Segoe UI", 12));
        msgLabel.setStyle("-fx-text-fill: #374151;");
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(280);

        textBox.getChildren().addAll(titleLabel, msgLabel);
        header.getChildren().addAll(iconLabel, textBox);

        // Progress bar + timer
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER);

        Label timerLabel = new Label(formatTime(durationSeconds));
        timerLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("Fechar");
        closeBtn.setStyle(
            "-fx-background-color: #f3f4f6;" +
            "-fx-text-fill: #374151;" +
            "-fx-font-size: 11px;" +
            "-fx-padding: 4 12 4 12;" +
            "-fx-background-radius: 6px;" +
            "-fx-cursor: hand;"
        );
        closeBtn.setOnAction(e -> dismiss());

        footer.getChildren().addAll(timerLabel, spacer, closeBtn);
        root.getChildren().addAll(header, footer);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);

        // Posição: canto inferior esquerdo
        positionBottomLeft();

        // Animação de entrada
        root.setOpacity(0);
        stage.show();

        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // Countdown timer
        final int[] remaining = {durationSeconds};
        Timeline countdown = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                remaining[0]--;
                timerLabel.setText(formatTime(remaining[0]));
                if (remaining[0] <= 0) {
                    dismiss();
                }
            })
        );
        countdown.setCycleCount(durationSeconds);
        countdown.play();

        log.info("Toast notification exibido: {} ({}s)", title, durationSeconds);
    }

    private void dismiss() {
        if (stage == null) return;
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), stage.getScene().getRoot());
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            stage.close();
            log.info("Toast notification fechado: {}", title);
        });
        fadeOut.play();
    }

    private void positionBottomLeft() {
        // Detectar dimensão da tela
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        int screenWidth = gd.getDisplayMode().getWidth();
        int screenHeight = gd.getDisplayMode().getHeight();

        // Taskbar ≈ 40px, margin 20px
        double x = 20;
        double y = screenHeight - 180; // altura aprox do popup

        stage.setX(x);
        stage.setY(y);
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        if (m > 0) return String.format("Fecha em %dm %ds", m, s);
        return String.format("Fecha em %ds", s);
    }
}
