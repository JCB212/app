package br.com.infoativa.fiscal.report;

import br.com.infoativa.fiscal.domain.Periodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;

public class CstCfopReportService {
    private static final Logger log = LoggerFactory.getLogger(CstCfopReportService.class);

    public void gerar(Connection conn, Periodo periodo, Path outputDir,
                       String nomeEmpresa, String cnpj) throws Exception {
        log.info("Gerando relatório CST/CFOP: {}", periodo.descricao());
        // TODO: implementar agrupamento por CST e CFOP
    }
}
