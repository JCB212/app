package br.com.infoativa.fiscal.report;

import br.com.infoativa.fiscal.domain.Periodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;

public class DevolucoesReportService {
    private static final Logger log = LoggerFactory.getLogger(DevolucoesReportService.class);

    public void gerar(Connection conn, Periodo periodo, Path outputDir,
                       String nomeEmpresa, String cnpj) throws Exception {
        log.info("Gerando relatório de devoluções: {}", periodo.descricao());
        // TODO: implementar notas de devolução (CFOP 1201, 1202, 2201, 2202)
    }
}
