package br.com.infoativa.fiscal.service;

import br.com.infoativa.fiscal.config.AppConfig;
import br.com.infoativa.fiscal.domain.EmailConfig;
import br.com.infoativa.fiscal.domain.XmlDocumentInfo;
import br.com.infoativa.fiscal.mail.EmailService;
import br.com.infoativa.fiscal.xml.XmlMetadataExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Monitoramento em tempo real das pastas de XML.
 * Detecta novas NFCe (modelo 65) em contingencia e notifica
 * via callback na UI + email ao tecnico.
 *
 * Usa WatchService do Java NIO para escutar mudancas no filesystem.
 * Roda em thread separada, nao bloqueia a UI.
 */
public class XmlMonitorService {

    private static final Logger log = LoggerFactory.getLogger(XmlMonitorService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread monitorThread;
    private final List<XmlDocumentInfo> contingencias = new CopyOnWriteArrayList<>();
    private Consumer<String> onEvent;
    private Consumer<XmlDocumentInfo> onContingencia;

    /**
     * Inicia monitoramento de uma pasta de XMLs.
     * @param xmlDir pasta raiz para monitorar (e subpastas)
     * @param onEvent callback para qualquer evento (log na UI)
     * @param onContingencia callback quando encontra contingencia
     */
    public void start(String xmlDir, Consumer<String> onEvent, Consumer<XmlDocumentInfo> onContingencia) {
        if (running.get()) {
            log.warn("Monitor ja esta rodando");
            return;
        }

        this.onEvent = onEvent;
        this.onContingencia = onContingencia;
        this.contingencias.clear();

        Path dir = Path.of(xmlDir);
        if (!Files.exists(dir)) {
            emit("[MONITOR] Pasta nao encontrada: " + xmlDir);
            return;
        }

        running.set(true);
        monitorThread = new Thread(() -> runMonitor(dir), "XML-Monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
        emit("[MONITOR] Iniciado - Monitorando: " + xmlDir);
        log.info("Monitor iniciado: {}", xmlDir);
    }

    public void stop() {
        running.set(false);
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        emit("[MONITOR] Parado");
        log.info("Monitor parado");
    }

    public boolean isRunning() {
        return running.get();
    }

    public List<XmlDocumentInfo> getContingencias() {
        return new ArrayList<>(contingencias);
    }

    /**
     * Gera relatorio de contingencias em TXT e retorna o path.
     */
    public Path gerarRelatorioContingencia(Path outputDir) throws IOException {
        Path file = outputDir.resolve("Relatorio_Contingencia_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write("RELATORIO DE CONTINGENCIAS - NFCe (Modelo 65)");
            w.newLine();
            w.write("Gerado em: " + LocalDateTime.now().format(DTF));
            w.newLine();
            w.write("Total de contingencias detectadas: " + contingencias.size());
            w.newLine();
            w.write("=".repeat(80));
            w.newLine();
            w.newLine();

            for (XmlDocumentInfo xml : contingencias) {
                w.write("Arquivo: " + xml.filePath().getFileName());
                w.newLine();
                w.write("  Chave: " + (xml.chaveAcesso() != null ? xml.chaveAcesso() : "N/A"));
                w.newLine();
                w.write("  Numero: " + (xml.numero() != null ? xml.numero() : "N/A"));
                w.newLine();
                w.write("  Serie: " + (xml.serie() != null ? xml.serie() : "N/A"));
                w.newLine();
                w.write("  Modelo: " + (xml.modelo() != null ? xml.modelo() : "N/A"));
                w.newLine();
                w.write("  Emissao: " + (xml.dataEmissao() != null ? xml.dataEmissao().format(DTF) : "N/A"));
                w.newLine();
                w.write("  Recebimento: " + (xml.dataRecebimento() != null ? xml.dataRecebimento().format(DTF) : "N/A"));
                w.newLine();
                w.write("  Tipo Contingencia: " + (xml.tipoContingencia() != null ? xml.tipoContingencia() : "Detectada por diferenca dhEmi/dhRecbto"));
                w.newLine();
                w.write("  Status: " + (xml.status() != null ? xml.status() : "N/A"));
                w.newLine();
                w.write("  Protocolo: " + (xml.protocolo() != null ? xml.protocolo() : "N/A"));
                w.newLine();

                if (xml.hasDateDiscrepancy()) {
                    long diffMin = java.time.Duration.between(xml.dataEmissao(), xml.dataRecebimento()).toMinutes();
                    w.write("  ** ALERTA: Diferenca emissao/recebimento: " + diffMin + " minutos **");
                    w.newLine();
                }
                w.write("-".repeat(60));
                w.newLine();
            }
        }
        log.info("Relatorio de contingencia gerado: {}", file);
        return file;
    }

    /**
     * Envia relatorio de contingencia por email ao tecnico.
     */
    public boolean enviarRelatorioTecnico(EmailConfig emailConfig, String emailTecnico, Path relatorio) {
        if (emailTecnico == null || emailTecnico.isBlank()) {
            log.warn("Email do tecnico nao configurado");
            return false;
        }
        return EmailService.sendZip(emailConfig, List.of(emailTecnico), relatorio,
            "ALERTA: " + contingencias.size() + " NFCe em contingencia detectadas");
    }

    private void runMonitor(Path dir) {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            // Registra a pasta e subpastas
            registerRecursive(dir, watcher);

            emit("[MONITOR] Escutando mudancas em " + dir.getFileName() + "...");

            while (running.get()) {
                WatchKey key;
                try {
                    key = watcher.poll(2, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    break;
                }

                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    Path changed = ((WatchEvent<Path>) event).context();
                    Path fullPath = ((Path) key.watchable()).resolve(changed);

                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (fullPath.toString().toLowerCase().endsWith(".xml")) {
                            processNewXml(fullPath);
                        } else if (Files.isDirectory(fullPath)) {
                            // Nova subpasta - registrar tambem
                            try {
                                fullPath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                            } catch (IOException ignored) {}
                        }
                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        if (fullPath.toString().toLowerCase().endsWith(".xml")) {
                            processNewXml(fullPath);
                        }
                    }
                }

                if (!key.reset()) break;
            }
        } catch (IOException e) {
            log.error("Erro no monitor: {}", e.getMessage());
            emit("[MONITOR] Erro: " + e.getMessage());
        }

        running.set(false);
        emit("[MONITOR] Encerrado");
    }

    private void processNewXml(Path xmlPath) {
        // Aguarda arquivo ser completamente escrito
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        XmlDocumentInfo info = XmlMetadataExtractor.extract(xmlPath);
        if (info == null) return;

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // So verifica contingencia para NFCe (modelo 65)
        if (info.isNfce()) {
            if (info.contingencia()) {
                contingencias.add(info);
                String msg = "[" + ts + "] CONTINGENCIA DETECTADA! NFCe " + info.numero()
                    + " - Tipo: " + (info.tipoContingencia() != null ? info.tipoContingencia() : "DESCONHECIDO")
                    + " - " + xmlPath.getFileName();
                emit(msg);
                if (onContingencia != null) onContingencia.accept(info);
                log.warn("Contingencia NFCe: {} tipo={}", info.numero(), info.tipoContingencia());
            } else if (info.hasDateDiscrepancy()) {
                contingencias.add(info);
                long diff = java.time.Duration.between(info.dataEmissao(), info.dataRecebimento()).toMinutes();
                String msg = "[" + ts + "] ALERTA: NFCe " + info.numero()
                    + " - Diferenca emissao/recebimento: " + diff + " min - " + xmlPath.getFileName();
                emit(msg);
                if (onContingencia != null) onContingencia.accept(info);
            } else if (info.cancelado()) {
                emit("[" + ts + "] NFCe CANCELADA: " + info.numero() + " - " + xmlPath.getFileName());
            } else {
                emit("[" + ts + "] NFCe OK: " + info.numero() + " - " + xmlPath.getFileName());
            }
        } else if (info.isNfe()) {
            emit("[" + ts + "] NFe detectada: " + info.numero() + " - " + xmlPath.getFileName());
        }
    }

    private void registerRecursive(Path root, WatchService watcher) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void emit(String msg) {
        if (onEvent != null) onEvent.accept(msg);
    }
}
