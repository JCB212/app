package br.com.infoativa.fiscal.zip;

import org.apache.commons.compress.archivers.zip.*;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serviço de compactação ZIP usando Apache Commons Compress.
 *
 * Funcionalidades:
 *   - ZIP com nível de compressão configurável (0-9)
 *   - Preserva estrutura de pastas
 *   - Suporte a arquivos grandes (streaming, sem carregar em memória)
 *   - Relatório de arquivos incluídos e tamanho total
 *   - Charset UTF-8 nos nomes de entrada
 */
public final class ZipService {

    private static final Logger log = LoggerFactory.getLogger(ZipService.class);

    // Nível de compressão: BEST_SPEED (1) ou BEST_COMPRESSION (9)
    private static final int COMPRESSION_LEVEL = 6;

    private ZipService() {}

    /**
     * Compacta todos os arquivos de um diretório em um arquivo ZIP.
     * Preserva a estrutura de subpastas relativa ao diretório raiz.
     *
     * @param sourceDir  Diretório fonte (recursivo)
     * @param zipFile    Arquivo ZIP de destino (será sobrescrito se existir)
     * @return Path do ZIP gerado
     */
    public static Path zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Diretório fonte não existe: " + sourceDir);
        }

        Files.createDirectories(zipFile.getParent());
        if (Files.exists(zipFile)) Files.delete(zipFile);

        AtomicLong totalFiles = new AtomicLong(0);
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicLong zipBytes   = new AtomicLong(0);

        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipFile), 65536))) {

            zos.setLevel(COMPRESSION_LEVEL);
            zos.setEncoding("UTF-8");
            zos.setUseLanguageEncodingFlag(true);
            zos.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);

            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Não incluir o próprio ZIP no ZIP
                    if (file.equals(zipFile)) return FileVisitResult.CONTINUE;

                    String entryName = sourceDir.relativize(file)
                            .toString().replace('\\', '/');

                    ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
                    entry.setSize(attrs.size());
                    entry.setLastModifiedTime(attrs.lastModifiedTime());
                    entry.setMethod(ZipArchiveEntry.DEFLATED);
                    entry.setUnixMode(0644);

                    zos.putArchiveEntry(entry);

                    try (InputStream in = new BufferedInputStream(
                            Files.newInputStream(file), 65536)) {
                        long copied = IOUtils.copy(in, zos);
                        totalBytes.addAndGet(copied);
                        totalFiles.incrementAndGet();
                    }

                    zos.closeArchiveEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("Arquivo ignorado no ZIP: {} ({})", file.getFileName(),
                             exc != null ? exc.getMessage() : "erro");
                    return FileVisitResult.CONTINUE;
                }
            });

            zos.finish();
        }

        zipBytes.set(Files.size(zipFile));
        double ratio = totalBytes.get() > 0
                       ? (1.0 - (double) zipBytes.get() / totalBytes.get()) * 100
                       : 0;

        log.info("ZIP gerado: {} arquivos, {:.1f} KB → {:.1f} KB (compressão {:.1f}%)",
                 totalFiles.get(),
                 totalBytes.get() / 1024.0,
                 zipBytes.get() / 1024.0,
                 ratio);

        return zipFile;
    }

    /**
     * Compacta arquivos específicos (lista) em um ZIP.
     *
     * @param arquivos  Lista de arquivos a incluir
     * @param zipFile   Arquivo ZIP de destino
     * @param prefixo   Prefixo de pasta dentro do ZIP (ex: "Fiscal/")
     * @return Path do ZIP gerado
     */
    public static Path zipArquivos(Iterable<Path> arquivos, Path zipFile,
                                    String prefixo) throws IOException {
        Files.createDirectories(zipFile.getParent());
        if (Files.exists(zipFile)) Files.delete(zipFile);

        String prefix = prefixo != null && !prefixo.isBlank()
                        ? (prefixo.endsWith("/") ? prefixo : prefixo + "/")
                        : "";

        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipFile), 65536))) {
            zos.setLevel(COMPRESSION_LEVEL);
            zos.setEncoding("UTF-8");
            zos.setUseLanguageEncodingFlag(true);

            for (Path arquivo : arquivos) {
                if (!Files.exists(arquivo) || Files.isDirectory(arquivo)) continue;

                String entryName = prefix + arquivo.getFileName().toString();
                ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
                entry.setSize(Files.size(arquivo));
                zos.putArchiveEntry(entry);

                try (InputStream in = new BufferedInputStream(Files.newInputStream(arquivo))) {
                    IOUtils.copy(in, zos);
                }
                zos.closeArchiveEntry();
            }
            zos.finish();
        }

        log.info("ZIP de arquivos gerado: {}", zipFile.getFileName());
        return zipFile;
    }

    /**
     * Retorna o nome padrão do ZIP para um período fiscal.
     * Ex: Fechamento_Fiscal_03_2026_20260401_143022.zip
     */
    public static String nomePadraoZip(String mesAnoRef) {
        String ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "Fechamento_Fiscal_" + mesAnoRef + "_" + ts + ".zip";
    }
}
