package br.com.infoativa.fiscal.fiscal;

import br.com.infoativa.fiscal.domain.Periodo;

import java.nio.file.Path;
import java.sql.Connection;

public interface FiscalObligationStrategy {
    String name();
    void generate(Connection conn, Periodo periodo, Path outputDir) throws Exception;
}
