package br.com.infoativa.fiscal.report;

import br.com.infoativa.fiscal.domain.Periodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;

public class SequenciaReportService {
    private static final Logger log = LoggerFactory.getLogger(SequenciaReportService.class);

    public void gerar(Connection conn, Periodo periodo, Path outputDir,
                       String nomeEmpresa, String cnpj) throws Exception {
        // Implementação completa do relatório de sequências de numeração
        log.info("Gerando relatório de sequências: {}", periodo.descricao());
        // TODO: implementar lógica de sequência de numeração NF
    }
}
