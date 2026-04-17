package br.com.infoativa.fiscal.tray;

import br.com.infoativa.fiscal.config.AppConfig;
import br.com.infoativa.fiscal.config.IniManager;
import br.com.infoativa.fiscal.domain.Periodo;
import br.com.infoativa.fiscal.service.ContingencyMonitorService;
import br.com.infoativa.fiscal.service.StartupService;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gerencia o ícone da bandeja do sistema (System Tray).
 * O programa fica minimizado na bandeja, monitorando 24/7.
 * - Detecta contingência e notifica técnico a cada 3h
 * - Inicia com o Windows (via registro)
 * - Fica oculto até arquivos serem enviados ao contador
 * - Após envio, exibe popup profissional no canto inferior esquerdo por 2min
 */
public class SystemTrayManager {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManager.class);
    private static SystemTrayManager instance;

    private TrayIcon trayIcon;
    private SystemTray systemTray;
    private PopupMenu trayMenu;
    private ContingencyMonitorService contingencyMonitor;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean arquivosEnviados = new AtomicBoolean(false);

    private Runnable onShowApp;
    private Runnable onHideApp;

    private SystemTrayManager() {}

    public static SystemTrayManager getInstance() {
        if (instance == null) {
            instance = new SystemTrayManager();
        }
        return instance;
    }

    public boolean isSupported() {
        return SystemTray.isSupported();
    }

    public void initialize(AppConfig config, Runnable onShowApp, Runnable onHideApp) {
        if (!isSupported()) {
            log.warn("System Tray nao suportado neste ambiente");
            return;
        }

        this.onShowApp = onShowApp;
        this.onHideApp = onHideApp;

        try {
            systemTray = SystemTray.getSystemTray();
            Image icon = createTrayIcon();
            buildTrayMenu();

            trayIcon = new TrayIcon(icon, "Módulo Fiscal InfoAtiva", trayMenu);
            trayIcon.setImageAutoSize(true);
            trayIcon.setToolTip("Módulo Fiscal InfoAtiva - Monitorando...");
            trayIcon.addActionListener(e -> showApp());

            systemTray.add(trayIcon);
            log.info("System Tray inicializado com sucesso");

            // Iniciar monitoramento de contingência
            startContingencyMonitor(config);

            // Registrar no startup do Windows
            try {
                StartupService.registerStartup();
            } catch (Exception e) {
                log.warn("Nao foi possivel registrar no startup do Windows: {}", e.getMessage());
            }

        } catch (AWTException e) {
            log.error("Erro ao inicializar System Tray", e);
        }
    }

    private void buildTrayMenu() {
        trayMenu = new PopupMenu();

        MenuItem itemAbrir = new MenuItem("Abrir Módulo Fiscal");
        itemAbrir.addActionListener(e -> showApp());

        MenuItem itemStatus = new MenuItem("Status: Monitorando...");
        itemStatus.setEnabled(false);

        MenuItem itemEnviar = new MenuItem("Forçar Envio Agora");
        itemEnviar.addActionListener(e -> notifyForceSend());

        MenuItem separador = new MenuItem("-");
        separador.setEnabled(false);

        MenuItem itemFechar = new MenuItem("Fechar");
        itemFechar.addActionListener(e -> exitApplication());

        trayMenu.add(itemAbrir);
        trayMenu.addSeparator();
        trayMenu.add(itemStatus);
        trayMenu.add(itemEnviar);
        trayMenu.addSeparator();
        trayMenu.add(itemFechar);
    }

    private void startContingencyMonitor(AppConfig config) {
        contingencyMonitor = new ContingencyMonitorService(config);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "contingency-monitor");
            t.setDaemon(true);
            return t;
        });

        // Verificar contingência a cada 3 horas
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (contingencyMonitor.hasContingency()) {
                    log.warn("CONTINGENCIA DETECTADA - Notificando tecnico");
                    showContingencyNotification();
                }
            } catch (Exception e) {
                log.error("Erro no monitor de contingencia", e);
            }
        }, 0, 3, TimeUnit.HOURS);

        log.info("Monitor de contingencia iniciado (verificacao a cada 3 horas)");
    }

    /**
     * Exibe notificação nativa do Windows quando detectar contingência.
     * O técnico não precisa ter o programa aberto.
     */
    public void showContingencyNotification() {
        if (trayIcon == null) return;
        String msg = "⚠️ XMLs em CONTINGÊNCIA detectados!\n" +
                     "Acesse o Módulo Fiscal para regularizar.";
        trayIcon.displayMessage(
            "🔴 Alerta Fiscal - Contingência",
            msg,
            TrayIcon.MessageType.WARNING
        );
        log.info("Notificacao de contingencia exibida - {}", 
                 LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    /**
     * Exibe popup profissional no canto inferior esquerdo por 2 minutos
     * após envio dos arquivos fiscais ao contador.
     */
    public void showArquivosEnviadosPopup() {
        arquivosEnviados.set(true);
        Platform.runLater(() -> {
            FiscalToastNotification toast = new FiscalToastNotification(
                "✅ Arquivos Fiscais Enviados",
                "Os arquivos fiscais foram enviados com sucesso para o contador.",
                120 // 2 minutos
            );
            toast.show();
        });

        // Também notificação na bandeja
        if (trayIcon != null) {
            trayIcon.displayMessage(
                "✅ Módulo Fiscal",
                "Arquivos fiscais enviados com sucesso ao contador!",
                TrayIcon.MessageType.INFO
            );
        }
    }

    public void hideToTray() {
        if (onHideApp != null) {
            Platform.runLater(onHideApp);
        }
        setStatus("Executando em segundo plano...");
    }

    public void showApp() {
        if (onShowApp != null) {
            Platform.runLater(onShowApp);
        }
    }

    public void setStatus(String status) {
        if (trayIcon != null) {
            trayIcon.setToolTip("Módulo Fiscal InfoAtiva - " + status);
        }
        // Atualizar item de menu
        if (trayMenu != null && trayMenu.getItemCount() > 2) {
            MenuItem statusItem = trayMenu.getItem(2);
            if (statusItem != null) {
                statusItem.setLabel("Status: " + status);
            }
        }
    }

    private void notifyForceSend() {
        showApp();
        Platform.runLater(() -> {
            log.info("Forcado envio manual pelo usuario via tray");
        });
    }

    private void exitApplication() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
        }
        Platform.exit();
        System.exit(0);
    }

    public void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
        }
    }

    private Image createTrayIcon() {
        // Criar ícone programaticamente (azul fiscal)
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(26, 86, 219));
        g.fillRoundRect(1, 1, 14, 14, 4, 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 9));
        g.drawString("F", 5, 11);
        g.dispose();
        return img;
    }

    public boolean isArquivosEnviados() {
        return arquivosEnviados.get();
    }

    public void resetArquivosEnviados() {
        arquivosEnviados.set(false);
    }
}
