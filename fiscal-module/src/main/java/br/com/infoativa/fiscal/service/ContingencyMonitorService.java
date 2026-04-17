package br.com.infoativa.fiscal.service;

import br.com.infoativa.fiscal.config.AppConfig;
import br.com.infoativa.fiscal.domain.XmlDocumentInfo;
import br.com.infoativa.fiscal.xml.XmlMetadataExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serviço de monitoramento 24/7 de contingência.
 * Verifica XMLs nos diretórios configurados em busca de status CONTINGENCIA.
 * Chamado a cada 3 horas pelo SystemTrayManager.
 */
public class ContingencyMonitorService {

    private static final Logger log = LoggerFactory.getLogger(ContingencyMonitorService.class);
    private final AppConfig config;
    private LocalDateTime lastCheck;
    private int contingenciaCount = 0;

    public ContingencyMonitorService(AppConfig config) {
        this.config = config;
    }

    /**
     * Verifica se há XMLs em contingência no período atual e anterior.
     * @return true se houver contingência detectada
     */
    public boolean hasContingency() {
        List<String> dirs = List.of(
            config.caminhoXmlNfe(),
            config.caminhoXmlNfce()
        );

        LocalDate hoje = LocalDate.now();
        LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
        LocalDate inicioMesAnterior = hoje.minusMonths(1).withDayOfMonth(1);
        LocalDate fimMesAnterior = hoje.minusMonths(1).withDayOfMonth(
            hoje.minusMonths(1).lengthOfMonth()
        );

        AtomicInteger contingencias = new AtomicInteger(0);

        for (String dir : dirs) {
            if (dir == null || dir.isBlank()) continue;
            Path path = Path.of(dir);
            if (!Files.exists(path)) continue;

            try {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (!file.toString().toLowerCase().endsWith(".xml")) {
                            return FileVisitResult.CONTINUE;
                        }
                        try {
                            XmlDocumentInfo info = XmlMetadataExtractor.extract(file);
                            if (info == null || info.dataEmissao() == null) {
                                return FileVisitResult.CONTINUE;
                            }
                            LocalDate emissao = info.dataEmissao().toLocalDate();
                            // Verificar apenas mês atual e anterior
                            if (emissao.isBefore(inicioMesAnterior) || emissao.isAfter(hoje)) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (isContingencia(info.status())) {
                                contingencias.incrementAndGet();
                                log.warn("Contingencia detectada: {} - Status: {}", 
                                         file.getFileName(), info.status());
                            }
                        } catch (Exception e) {
                            // Ignorar XML inválido
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                log.error("Erro ao monitorar diretorio {}", dir, e);
            }
        }

        lastCheck = LocalDateTime.now();
        contingenciaCount = contingencias.get();

        if (contingenciaCount > 0) {
            log.warn("Monitoramento concluido: {} XMLs em contingencia encontrados", contingenciaCount);
        } else {
            log.debug("Monitoramento concluido: sem contingencias");
        }

        return contingenciaCount > 0;
    }

    private boolean isContingencia(String status) {
        if (status == null) return false;
        String s = status.toUpperCase().trim();
        return s.contains("CONTINGENCIA") || s.contains("CONTINGÊNCIA") ||
               s.equals("5") || s.equals("6") || s.equals("7") || s.equals("8") ||
               s.contains("DPEC") || s.contains("OFFLINE");
    }

    public LocalDateTime getLastCheck() {
        return lastCheck;
    }

    public int getContingenciaCount() {
        return contingenciaCount;
    }
}
