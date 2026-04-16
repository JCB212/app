package br.com.infoativa.fiscal.repository;

import br.com.infoativa.fiscal.domain.ParametrosRegistro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class ParametrosRepository {

    private static final Logger log = LoggerFactory.getLogger(ParametrosRepository.class);
    private final Connection conn;

    public ParametrosRepository(Connection conn) {
        this.conn = conn;
    }

    public ParametrosRegistro findFirst() {
        String sql = """
            SELECT FIRST 1 ID, ALIQUOTA_ICMS, NATUREZA_OPERACAO_NFE,
                   NFE_CERTIFICADO_NUMSERIE, NFE_WEBSERVICE_UF, NFE_WEBSERVICE_AMBIENTE,
                   NFE_DANFE_LOGOMARCA, NFE_SERIE,
                   NFE_EMAIL_SMTPHOST, NFE_EMAIL_SMTPPORT, NFE_EMAIL_SMTPUSER,
                   NFE_EMAIL_SMTPPASS, NFE_EMAIL_ASSUNTO, NFE_EMAIL_MSG,
                   NFE_EMAIL_ENVIO, NFE_EMAIL_SSL, NFE_EMAIL_TLS,
                   MODO_SENHA, VERSAO_BANCO, HASH_S, ENVIO_XML, ATIVIDADE_IBSCBS
            FROM PARAMETROS
            ORDER BY ID
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                ParametrosRegistro p = new ParametrosRegistro(
                    rs.getLong("ID"),
                    safe(rs, "ALIQUOTA_ICMS"),
                    safe(rs, "NATUREZA_OPERACAO_NFE"),
                    safe(rs, "NFE_CERTIFICADO_NUMSERIE"),
                    safe(rs, "NFE_WEBSERVICE_UF"),
                    safe(rs, "NFE_WEBSERVICE_AMBIENTE"),
                    safe(rs, "NFE_DANFE_LOGOMARCA"),
                    safe(rs, "NFE_SERIE"),
                    safe(rs, "NFE_EMAIL_SMTPHOST"),
                    safe(rs, "NFE_EMAIL_SMTPPORT"),
                    safe(rs, "NFE_EMAIL_SMTPUSER"),
                    safe(rs, "NFE_EMAIL_SMTPPASS"),
                    safe(rs, "NFE_EMAIL_ASSUNTO"),
                    safe(rs, "NFE_EMAIL_MSG"),
                    safe(rs, "NFE_EMAIL_ENVIO"),
                    safe(rs, "NFE_EMAIL_SSL"),
                    safe(rs, "NFE_EMAIL_TLS"),
                    safe(rs, "MODO_SENHA"),
                    safe(rs, "VERSAO_BANCO"),
                    safe(rs, "HASH_S"),
                    safe(rs, "ENVIO_XML"),
                    safe(rs, "ATIVIDADE_IBSCBS"),
                    ""
                );
                log.info("Parametros carregados - UF: {}, Versao: {}", p.nfeWebserviceUf(), p.versaoBanco());
                return p;
            }
        } catch (SQLException e) {
            log.error("Erro ao buscar parametros: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Busca o nome da empresa a partir do emitente da primeira NFe autorizada
     */
    public String findNomeEmpresa() {
        // Try to get from NFe emitente info (informacoes_cpl often has it)
        // Or we get the CNPJ from the chave de acesso
        String sql = "SELECT FIRST 1 NFE_CHAVE_ACESSO FROM NFE WHERE NFE_STATUS = 'AUTORIZADO' AND NFE_CHAVE_ACESSO IS NOT NULL ORDER BY ID";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String chave = rs.getString(1);
                if (chave != null && chave.length() >= 25) {
                    String cnpj = chave.substring(6, 20);
                    return findNomeByCnpj(cnpj);
                }
            }
        } catch (SQLException e) {
            log.warn("Erro ao buscar nome empresa: {}", e.getMessage());
        }
        return "Empresa";
    }

    private String findNomeByCnpj(String cnpj) {
        // Try CLIENTE table first (if exists)
        try {
            String sql = "SELECT FIRST 1 NOME FROM CLIENTE WHERE CPF_CNPJ = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, cnpj);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String nome = rs.getString(1);
                if (nome != null && !nome.isBlank()) return nome.trim();
            }
        } catch (SQLException ignored) {}
        return "Empresa CNPJ " + cnpj;
    }

    private String safe(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        return v != null ? v.trim() : "";
    }
}
