package br.com.infoativa.fiscal.xml;

import br.com.infoativa.fiscal.domain.Periodo;
import br.com.infoativa.fiscal.domain.XmlDocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Scanner de XMLs que filtra EXCLUSIVAMENTE pela data de emissao (dhEmi)
 * lida de dentro do proprio XML. NAO usa data de modificacao do arquivo.
 *
 * Isso garante que ao selecionar Janeiro, somente XMLs com dhEmi em Janeiro
 * serao incluidos, independente de quando o arquivo foi criado/modificado.
 */
public class XmlScanService {

    private static final Logger log = LoggerFactory.getLogger(XmlScanService.class);

    public List<XmlDocumentInfo> scan(String directory, Periodo periodo, Consumer<String> progressCallback) {
        List<XmlDocumentInfo> results = new ArrayList<>();
        Path dir = Path.of(directory);

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("Diretorio nao encontrado: {}", directory);
            if (progressCallback != null) {
                progressCallback.accept("AVISO: Diretorio nao encontrado: " + directory);
            }
            return results;
        }

        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger matched = new AtomicInteger(0);

        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!file.toString().toLowerCase().endsWith(".xml")) return FileVisitResult.CONTINUE;
                    int count = total.incrementAndGet();

                    // Extrai metadados REAIS do XML (dhEmi, modelo, status, etc.)
                    XmlDocumentInfo info = XmlMetadataExtractor.extract(file);

                    if (info != null && info.dataEmissao() != null) {
                        // Filtra EXCLUSIVAMENTE pela data de emissao do XML
                        LocalDate emissao = info.dataEmissao().toLocalDate();
                        if (!emissao.isBefore(periodo.inicio()) && !emissao.isAfter(periodo.fim())) {
                            results.add(info);
                            matched.incrementAndGet();
                        }
                    }

                    if (count % 200 == 0 && progressCallback != null) {
                        progressCallback.accept("Escaneando: " + count + " XMLs verificados, " + matched.get() + " no periodo...");
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("Nao foi possivel ler: {}", file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Erro ao escanear diretorio {}: {}", directory, e.getMessage());
        }

        log.info("Escaneamento: {} XMLs encontrados de {} verificados em {}", results.size(), total.get(), directory);
        if (progressCallback != null) {
            progressCallback.accept("Encontrados " + results.size() + " XMLs no periodo de " + total.get() + " verificados em " + dir.getFileName());
        }
        return results;
    }

    public void copyToOutput(List<XmlDocumentInfo> xmls, Path outputDir) throws IOException {
        Path nfePath = outputDir.resolve("NFe");
        Path nfcePath = outputDir.resolve("NFCe");
        Path comprasPath = outputDir.resolve("Compras");
        Path canceladosPath = outputDir.resolve("Cancelados");
        Path inutilizadosPath = outputDir.resolve("Inutilizados");
        Path contingenciaPath = outputDir.resolve("Contingencia");

        Files.createDirectories(nfePath);
        Files.createDirectories(nfcePath);
        Files.createDirectories(comprasPath);
        Files.createDirectories(canceladosPath);
        Files.createDirectories(inutilizadosPath);
        Files.createDirectories(contingenciaPath);

        int copied = 0;
        for (XmlDocumentInfo xml : xmls) {
            Path dest;
            if (xml.cancelado()) {
                dest = canceladosPath;
            } else if (xml.inutilizado()) {
                dest = inutilizadosPath;
            } else if (xml.contingencia()) {
                dest = contingenciaPath;
            } else if (xml.isNfe()) {
                String pathStr = xml.filePath().toString().toLowerCase();
                if (pathStr.contains("fornecedor") || pathStr.contains("compra") || pathStr.contains("entrada")) {
                    dest = comprasPath;
                } else {
                    dest = nfePath;
                }
            } else if (xml.isNfce()) {
                dest = nfcePath;
            } else {
                dest = nfePath;
            }

            try {
                Files.copy(xml.filePath(), dest.resolve(xml.filePath().getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (IOException e) {
                log.warn("Erro ao copiar {}: {}", xml.filePath().getFileName(), e.getMessage());
            }
        }
        log.info("{} XMLs copiados para as pastas de saida", copied);
    }
}
