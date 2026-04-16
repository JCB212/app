package br.com.infoativa.fiscal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class OutputStructureService {

    private static final Logger log = LoggerFactory.getLogger(OutputStructureService.class);

    public static Path createStructure(String baseName) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"), "XMLContabilidade", baseName);
        Files.createDirectories(root.resolve("NFe"));
        Files.createDirectories(root.resolve("NFCe"));
        Files.createDirectories(root.resolve("Compras"));
        Files.createDirectories(root.resolve("Cancelados"));
        Files.createDirectories(root.resolve("Inutilizados"));
        Files.createDirectories(root.resolve("TXT"));
        Files.createDirectories(root.resolve("PDF"));
        log.info("Estrutura de saida criada: {}", root);
        return root;
    }

    public static Path getXmlContabilidadeRoot() {
        return Path.of(System.getProperty("user.dir"), "XMLContabilidade");
    }
}
