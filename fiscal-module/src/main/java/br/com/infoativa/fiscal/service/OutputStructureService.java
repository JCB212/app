package br.com.infoativa.fiscal.service;

import java.io.IOException;
import java.nio.file.*;

public final class OutputStructureService {

    private static final String BASE_DIR = System.getProperty("user.dir");

    private OutputStructureService() {}

    public static Path createStructure(String mesAnoRef) throws IOException {
        Path root = Path.of(BASE_DIR, "XMLContabilidade", mesAnoRef);
        for (String sub : new String[]{"NFe", "NFCe", "Compras", "Cancelados",
                                        "Inutilizados", "Contingencia", "PDF", "TXT"}) {
            Files.createDirectories(root.resolve(sub));
        }
        return root;
    }
}
