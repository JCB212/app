package br.com.infoativa.fiscal.zip;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class ZipService {

    private static final Logger log = LoggerFactory.getLogger(ZipService.class);

    public static Path zipDirectory(Path sourceDir, Path outputZip) throws IOException {
        Files.createDirectories(outputZip.getParent());

        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(
                Files.newOutputStream(outputZip))) {
            zos.setLevel(6);

            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = sourceDir.relativize(file).toString().replace("\\", "/");
                    ZipArchiveEntry entry = new ZipArchiveEntry(file, entryName);
                    zos.putArchiveEntry(entry);
                    Files.copy(file, zos);
                    zos.closeArchiveEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(sourceDir)) {
                        String entryName = sourceDir.relativize(dir).toString().replace("\\", "/") + "/";
                        zos.putArchiveEntry(new ZipArchiveEntry(entryName));
                        zos.closeArchiveEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        log.info("ZIP criado: {} ({} bytes)", outputZip, Files.size(outputZip));
        return outputZip;
    }
}
