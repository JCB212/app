package br.com.infoativa.fiscal.xml;

import br.com.infoativa.fiscal.domain.Periodo;
import br.com.infoativa.fiscal.domain.XmlDocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Serviço de varredura de XMLs com:
 * - Pré-filtro por file.lastModified() (performance)
 * - Processamento em stream (sem carregar tudo em memória)
 * - Integração com cache XML_PROCESSADOS
 * - Filtro por período via dhEmi (não confiar só no arquivo)
 * - Detecção de mudança de CONTINGENCIA → AUTORIZADA
 */
public class XmlScanService {

    private static final Logger log = LoggerFactory.getLogger(XmlScanService.class);

    /**
     * Varre diretório e retorna XMLs válidos no período.
     * Usa stream sem carregar todos em memória.
     */
    public List<XmlDocumentInfo> scan(String directory, Periodo periodo, Consumer<String> progress) {
        List<XmlDocumentInfo> results = new ArrayList<>();
        if (directory == null || directory.isBlank()) return results;

        Path dir = Path.of(directory);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("Diretorio nao encontrado ou invalido: {}", directory);
            return results;
        }

        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger matched = new AtomicInteger(0);
        AtomicInteger ignored = new AtomicInteger(0);

        // Janela de pré-filtro: 2 meses antes do inicio e 1 mês após o fim
        LocalDate preFilterInicio = periodo.inicio().minusMonths(2);
        LocalDate preFilterFim    = periodo.fim().plusMonths(1);

        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString().toLowerCase();
                    if (!name.endsWith(".xml")) return FileVisitResult.CONTINUE;

                    total.incrementAndGet();

                    // ── Pré-filtro por data de modificação do arquivo ──────────
                    LocalDate fileModDate = attrs.lastModifiedTime().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                    if (fileModDate.isBefore(preFilterInicio) || fileModDate.isAfter(preFilterFim)) {
                        ignored.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }

                    // ── Leitura de metadados XML (stream, não DOM completo) ────
                    XmlDocumentInfo info = XmlMetadataExtractor.extract(file);
                    if (info == null || info.dataEmissao() == null) {
                        log.debug("XML sem data de emissao ignorado: {}", file.getFileName());
                        return FileVisitResult.CONTINUE;
                    }

                    // ── Filtrar SEMPRE por dhEmi ───────────────────────────────
                    LocalDate emissao = info.dataEmissao().toLocalDate();
                    if (emissao.isBefore(periodo.inicio()) || emissao.isAfter(periodo.fim())) {
                        ignored.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }

                    results.add(info);
                    int m = matched.incrementAndGet();

                    // Callback de progresso a cada 50 arquivos
                    if (m % 50 == 0 && progress != null) {
                        progress.accept("Escaneando: " + m + " XMLs no período encontrados...");
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("Nao foi possivel acessar: {} - {}", file, exc != null ? exc.getMessage() : "erro");
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Erro ao escanear diretorio: {}", directory, e);
        }

        log.info("Escaneamento concluido em '{}': {}/{} XMLs no periodo ({} pre-filtrados)",
                 directory, matched.get(), total.get(), ignored.get());
        return results;
    }

    /**
     * Varre XMLs e atualiza cache XML_PROCESSADOS.
     * Detecta mudanças de status (contingência → autorizado).
     */
    public void scanAndUpdateCache(String directory, Periodo periodo,
                                    XmlCacheRepository cache, Consumer<String> progress) {
        if (directory == null || directory.isBlank()) return;

        Path dir = Path.of(directory);
        if (!Files.exists(dir)) return;

        AtomicInteger inserted = new AtomicInteger(0);
        AtomicInteger updated  = new AtomicInteger(0);
        AtomicInteger ignored  = new AtomicInteger(0);
        AtomicInteger errors   = new AtomicInteger(0);

        LocalDate preFilterInicio = periodo.inicio().minusMonths(2);
        LocalDate preFilterFim    = periodo.fim().plusMonths(1);

        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!file.toString().toLowerCase().endsWith(".xml")) return FileVisitResult.CONTINUE;

                    LocalDate fileModDate = attrs.lastModifiedTime().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                    if (fileModDate.isBefore(preFilterInicio) || fileModDate.isAfter(preFilterFim)) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        XmlDocumentInfo info = XmlMetadataExtractor.extract(file);
                        if (info == null || info.dataEmissao() == null ||
                            info.chaveAcesso() == null || info.chaveAcesso().isBlank()) {
                            return FileVisitResult.CONTINUE;
                        }

                        LocalDate emissao = info.dataEmissao().toLocalDate();
                        if (emissao.isBefore(periodo.inicio()) || emissao.isAfter(periodo.fim())) {
                            return FileVisitResult.CONTINUE;
                        }

                        // Calcular HASH do conteúdo
                        byte[] content = Files.readAllBytes(file);
                        String hash = XmlCacheRepository.computeHash(content);

                        // Processar cache
                        XmlCacheRepository.ProcessResult result = cache.process(
                            info.chaveAcesso(), emissao,
                            file.toString(), info.status(), hash
                        );

                        switch (result) {
                            case INSERTED -> inserted.incrementAndGet();
                            case UPDATED  -> { updated.incrementAndGet(); log.info("XML atualizado ({}): {}", info.status(), info.chaveAcesso()); }
                            case IGNORED  -> ignored.incrementAndGet();
                            case ERROR    -> errors.incrementAndGet();
                        }
                    } catch (IOException e) {
                        log.warn("Erro ao processar XML para cache: {} - {}", file.getFileName(), e.getMessage());
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Erro ao varrer diretorio para cache: {}", directory, e);
        }

        log.info("Cache atualizado: {} inseridos, {} atualizados, {} ignorados, {} erros",
                 inserted.get(), updated.get(), ignored.get(), errors.get());
        if (progress != null) {
            progress.accept(String.format("Cache XML: +%d novos, %d atualizados", 
                            inserted.get(), updated.get()));
        }
    }

    /**
     * Copia XMLs para estrutura organizada de saída.
     */
    public void copyToOutput(List<XmlDocumentInfo> xmls, Path outputDir) {
        Path nfePath       = outputDir.resolve("NFe");
        Path nfcePath      = outputDir.resolve("NFCe");
        Path comprasPath   = outputDir.resolve("Compras");
        Path canceladosPath = outputDir.resolve("Cancelados");
        Path inutilizadosPath = outputDir.resolve("Inutilizados");

        try {
            Files.createDirectories(nfePath);
            Files.createDirectories(nfcePath);
            Files.createDirectories(comprasPath);
            Files.createDirectories(canceladosPath);
            Files.createDirectories(inutilizadosPath);
        } catch (IOException e) {
            log.error("Erro ao criar estrutura de diretórios de saida", e);
            return;
        }

        int copied = 0, errors = 0;
        for (XmlDocumentInfo xml : xmls) {
            try {
                Path dest;
                if (xml.inutilizado()) {
                    dest = inutilizadosPath;
                } else if (xml.cancelado()) {
                    dest = canceladosPath;
                } else if (xml.isNfce()) {
                    dest = nfcePath;
                } else if (xml.isNfe()) {
                    // Detectar compras pelo caminho
                    String pathStr = xml.filePath().toString().toLowerCase();
                    if (pathStr.contains("fornecedor") || pathStr.contains("compra") ||
                        pathStr.contains("entrada")) {
                        dest = comprasPath;
                    } else {
                        dest = nfePath;
                    }
                } else {
                    dest = nfePath;
                }
                Files.copy(xml.filePath(), dest.resolve(xml.filePath().getFileName()),
                           StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (IOException e) {
                log.warn("Erro ao copiar XML {}: {}", xml.filePath().getFileName(), e.getMessage());
                errors++;
            }
        }
        log.info("XMLs copiados para saida: {} ok, {} erros", copied, errors);
    }
}
