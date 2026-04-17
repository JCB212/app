package br.com.infoativa.fiscal.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Repositório para a tabela XML_PROCESSADOS.
 * Implementa cache inteligente: INSERT / UPDATE / IGNORE baseado em HASH e STATUS.
 * Auto-cria a tabela e índices se não existirem.
 */
public class XmlCacheRepository {

    private static final Logger log = LoggerFactory.getLogger(XmlCacheRepository.class);
    private final Connection connection;

    public XmlCacheRepository(Connection connection) {
        this.connection = connection;
    }

    // ── Auto-criação da estrutura ─────────────────────────────────────────────

    public void ensureTableExists() {
        String createTable = """
            CREATE TABLE IF NOT EXISTS XML_PROCESSADOS (
                CHAVE           VARCHAR(44)  NOT NULL,
                DATA_EMISSAO    DATE,
                CAMINHO         VARCHAR(500),
                STATUS          VARCHAR(30),
                HASH            VARCHAR(64),
                STATUS_PROCESSAMENTO VARCHAR(10) DEFAULT 'OK',
                DATA_PROCESSAMENTO   TIMESTAMP,
                DATA_ATUALIZACAO     TIMESTAMP,
                CONSTRAINT PK_XML_PROCESSADOS PRIMARY KEY (CHAVE)
            )
            """;

        // Firebird não suporta IF NOT EXISTS no DDL — usar exception handling
        try (Statement st = connection.createStatement()) {
            st.execute(createTable);
            log.info("Tabela XML_PROCESSADOS verificada/criada");
        } catch (SQLException e) {
            if (e.getErrorCode() == 335544351 || e.getMessage().contains("already exists")) {
                log.debug("Tabela XML_PROCESSADOS ja existe");
            } else {
                log.error("Erro ao criar tabela XML_PROCESSADOS", e);
            }
        }
        ensureIndexes();
    }

    private void ensureIndexes() {
        createIndexSafe("IDX_XML_DATA_EMISSAO", 
            "CREATE INDEX IDX_XML_DATA_EMISSAO ON XML_PROCESSADOS (DATA_EMISSAO)");
        createIndexSafe("IDX_XML_STATUS", 
            "CREATE INDEX IDX_XML_STATUS ON XML_PROCESSADOS (STATUS)");
        createIndexSafe("IDX_XML_STATUS_PROC",
            "CREATE INDEX IDX_XML_STATUS_PROC ON XML_PROCESSADOS (STATUS_PROCESSAMENTO)");
    }

    private void createIndexSafe(String name, String ddl) {
        try (Statement st = connection.createStatement()) {
            st.execute(ddl);
        } catch (SQLException e) {
            if (!e.getMessage().contains("already exists") && e.getErrorCode() != 335544351) {
                log.debug("Indice {} nao criado: {}", name, e.getMessage());
            }
        }
    }

    // ── Processamento inteligente INSERT / UPDATE / IGNORE ───────────────────

    public enum ProcessResult { INSERTED, UPDATED, IGNORED, ERROR }

    /**
     * Processa um XML: insere novo, atualiza se mudou, ignora se igual.
     */
    public ProcessResult process(String chave, LocalDate dataEmissao,
                                  String caminho, String status, String conteudoHash) {
        if (chave == null || chave.isBlank()) return ProcessResult.ERROR;

        try {
            XmlCacheEntry existing = findByChave(chave);

            if (existing == null) {
                insert(chave, dataEmissao, caminho, status, conteudoHash);
                log.debug("XML INSERIDO: chave={}, status={}", chave, status);
                return ProcessResult.INSERTED;
            }

            // Verificar se mudou (HASH ou STATUS)
            boolean hashChanged = !conteudoHash.equals(existing.hash());
            boolean statusChanged = !status.equals(existing.status());

            if (hashChanged || statusChanged) {
                update(chave, caminho, status, conteudoHash);
                if (statusChanged) {
                    log.info("XML ATUALIZADO (status: {} -> {}): chave={}", 
                             existing.status(), status, chave);
                    // Detectar mudança CONTINGENCIA -> AUTORIZADA
                    if (isContingencia(existing.status()) && isAutorizada(status)) {
                        log.info("CONTINGENCIA REGULARIZADA: chave={}", chave);
                    }
                } else {
                    log.debug("XML ATUALIZADO (hash mudou): chave={}", chave);
                }
                return ProcessResult.UPDATED;
            }

            log.debug("XML IGNORADO (sem alteracao): chave={}", chave);
            return ProcessResult.IGNORED;

        } catch (SQLException e) {
            log.error("Erro ao processar cache do XML: chave={}", chave, e);
            markError(chave);
            return ProcessResult.ERROR;
        }
    }

    // ── Consulta histórica ────────────────────────────────────────────────────

    public boolean existsInPeriod(LocalDate inicio, LocalDate fim) throws SQLException {
        String sql = "SELECT COUNT(*) FROM XML_PROCESSADOS " +
                     "WHERE DATA_EMISSAO BETWEEN ? AND ? " +
                     "AND STATUS_PROCESSAMENTO = 'OK'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(inicio));
            ps.setDate(2, Date.valueOf(fim));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public int countContingenciasNoResolvidas(LocalDate inicio, LocalDate fim) throws SQLException {
        String sql = "SELECT COUNT(*) FROM XML_PROCESSADOS " +
                     "WHERE DATA_EMISSAO BETWEEN ? AND ? " +
                     "AND (STATUS LIKE '%CONTINGENCIA%' OR STATUS LIKE '%CONTINGÊNCIA%')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(inicio));
            ps.setDate(2, Date.valueOf(fim));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    private XmlCacheEntry findByChave(String chave) throws SQLException {
        String sql = "SELECT CHAVE, DATA_EMISSAO, CAMINHO, STATUS, HASH, STATUS_PROCESSAMENTO " +
                     "FROM XML_PROCESSADOS WHERE CHAVE = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, chave);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new XmlCacheEntry(
                    rs.getString("CHAVE"),
                    rs.getDate("DATA_EMISSAO") != null ? rs.getDate("DATA_EMISSAO").toLocalDate() : null,
                    rs.getString("CAMINHO"),
                    rs.getString("STATUS"),
                    rs.getString("HASH"),
                    rs.getString("STATUS_PROCESSAMENTO")
                );
            }
        }
    }

    private void insert(String chave, LocalDate dataEmissao, String caminho,
                         String status, String hash) throws SQLException {
        String sql = "INSERT INTO XML_PROCESSADOS " +
                     "(CHAVE, DATA_EMISSAO, CAMINHO, STATUS, HASH, STATUS_PROCESSAMENTO, " +
                     "DATA_PROCESSAMENTO, DATA_ATUALIZACAO) VALUES (?,?,?,?,?,'OK',?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, chave);
            ps.setDate(2, dataEmissao != null ? Date.valueOf(dataEmissao) : null);
            ps.setString(3, caminho);
            ps.setString(4, status);
            ps.setString(5, hash);
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            ps.executeUpdate();
            connection.commit();
        }
    }

    private void update(String chave, String caminho, String status, String hash) throws SQLException {
        String sql = "UPDATE XML_PROCESSADOS SET CAMINHO=?, STATUS=?, HASH=?, " +
                     "DATA_ATUALIZACAO=?, STATUS_PROCESSAMENTO='OK' WHERE CHAVE=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, caminho);
            ps.setString(2, status);
            ps.setString(3, hash);
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(5, chave);
            ps.executeUpdate();
            connection.commit();
        }
    }

    private void markError(String chave) {
        try {
            String sql = "UPDATE XML_PROCESSADOS SET STATUS_PROCESSAMENTO='ERRO', " +
                         "DATA_ATUALIZACAO=? WHERE CHAVE=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(2, chave);
                ps.executeUpdate();
                connection.commit();
            }
        } catch (SQLException ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isContingencia(String status) {
        if (status == null) return false;
        return status.toUpperCase().contains("CONTINGENC");
    }

    private boolean isAutorizada(String status) {
        if (status == null) return false;
        String s = status.toUpperCase();
        return s.contains("AUTORIZA") || s.equals("100");
    }

    public static String computeHash(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "hash-error";
        }
    }

    // ── Record para entrada do cache ──────────────────────────────────────────

    public record XmlCacheEntry(
        String chave,
        LocalDate dataEmissao,
        String caminho,
        String status,
        String hash,
        String statusProcessamento
    ) {}
}
