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
     * Busca o nome da empresa usando múltiplas estratégias:
     * 1. Tabela EMITENTE (se existir)
     * 2. Tabela EMPRESA (se existir)
     * 3. CNPJ extraído da chave NFe -> busca na CLIENTE
     * 4. Fallback para "Empresa"
     */
    public String findNomeEmpresa() {
        // Strategy 1: Try EMITENTE table
        String nome = tryQuery("SELECT FIRST 1 RAZAO_SOCIAL FROM EMITENTE ORDER BY ID");
        if (nome == null) nome = tryQuery("SELECT FIRST 1 NOME FROM EMITENTE ORDER BY ID");
        if (nome == null) nome = tryQuery("SELECT FIRST 1 XNOME FROM EMITENTE ORDER BY ID");

        // Strategy 2: Try EMPRESA table
        if (nome == null) nome = tryQuery("SELECT FIRST 1 RAZAO_SOCIAL FROM EMPRESA ORDER BY ID");
        if (nome == null) nome = tryQuery("SELECT FIRST 1 NOME FROM EMPRESA ORDER BY ID");
        if (nome == null) nome = tryQuery("SELECT FIRST 1 NOME_FANTASIA FROM EMPRESA ORDER BY ID");

        // Strategy 3: Try CONFIGURACOES/CONFIG table
        if (nome == null) nome = tryQuery("SELECT FIRST 1 RAZAO_SOCIAL FROM CONFIGURACOES ORDER BY ID");
        if (nome == null) nome = tryQuery("SELECT FIRST 1 EMPRESA FROM CONFIGURACOES ORDER BY ID");

        // Strategy 4: Extract CNPJ from NFe chave and look in CLIENTE
        if (nome == null) {
            String chave = tryQuery("SELECT FIRST 1 NFE_CHAVE_ACESSO FROM NFE WHERE NFE_STATUS = 'AUTORIZADO' AND NFE_CHAVE_ACESSO IS NOT NULL ORDER BY ID");
            if (chave == null) {
                chave = tryQuery("SELECT FIRST 1 NFCE_CHAVE_ACESSO FROM NFCE WHERE NFCE_STATUS = 'AUTORIZADO' AND NFCE_CHAVE_ACESSO IS NOT NULL ORDER BY ID");
            }
            if (chave != null && chave.length() >= 25) {
                String cnpj = chave.substring(6, 20);
                nome = tryQueryParam("SELECT FIRST 1 NOME FROM CLIENTE WHERE CPF_CNPJ = ?", cnpj);
                if (nome == null) nome = tryQueryParam("SELECT FIRST 1 RAZAO_SOCIAL FROM CLIENTE WHERE CPF_CNPJ = ?", cnpj);
                if (nome == null) {
                    // Format CNPJ and use as company name
                    nome = "CNPJ " + cnpj.substring(0,2) + "." + cnpj.substring(2,5) + "." + cnpj.substring(5,8)
                         + "/" + cnpj.substring(8,12) + "-" + cnpj.substring(12,14);
                }
            }
        }

        return nome != null ? nome.trim() : "Empresa";
    }

    private String tryQuery(String sql) {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                String val = rs.getString(1);
                if (val != null && !val.isBlank()) return val.trim();
            }
        } catch (SQLException ignored) {
            // Table doesn't exist, try next strategy
        }
        return null;
    }

    private String tryQueryParam(String sql, String param) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String val = rs.getString(1);
                if (val != null && !val.isBlank()) return val.trim();
            }
        } catch (SQLException ignored) {}
        return null;
    }

    private String safe(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        return v != null ? v.trim() : "";
    }
}
