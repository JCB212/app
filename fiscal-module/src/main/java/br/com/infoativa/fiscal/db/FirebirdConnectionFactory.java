package br.com.infoativa.fiscal.db;

import br.com.infoativa.fiscal.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Factory de conexão Firebird com:
 * - Charset preferencial UTF8, fallback Cp1252 (Win1252)
 * - Timeout de conexão configurável
 * - Pool básico (singleton connection por gateway)
 * - try-with-resources friendly via DatabaseGateway
 */
public final class FirebirdConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(FirebirdConnectionFactory.class);
    private static final int CONNECTION_TIMEOUT_MS = 15000;
    private static final int SOCKET_TIMEOUT_MS = 60000;

    private FirebirdConnectionFactory() {}

    /**
     * Abre conexão com charset UTF8, fallback automático para Cp1252.
     */
    public static Connection open(AppConfig config) throws SQLException {
        // Tentar UTF8 primeiro
        try {
            Connection conn = openWithCharset(config, "UTF8");
            log.info("Conexao Firebird estabelecida (UTF8): {}:{}/{}", 
                     config.ipServidor(), config.porta(), config.basePath());
            return conn;
        } catch (SQLException e) {
            log.warn("Falha com charset UTF8, tentando Cp1252: {}", e.getMessage());
        }
        // Fallback: Cp1252 (Windows-1252)
        try {
            Connection conn = openWithCharset(config, "WIN1252");
            log.info("Conexao Firebird estabelecida (Cp1252/WIN1252): {}:{}/{}", 
                     config.ipServidor(), config.porta(), config.basePath());
            return conn;
        } catch (SQLException e) {
            log.error("Falha ao conectar ao Firebird com UTF8 e Cp1252", e);
            throw new SQLException(
                "Não foi possível conectar ao banco de dados Firebird em " +
                config.ipServidor() + ":" + config.porta() + ". " +
                "Verifique se o servidor está rodando e as configurações.", e);
        }
    }

    private static Connection openWithCharset(AppConfig config, String charset) throws SQLException {
        String url = buildUrl(config, charset);

        Properties props = new Properties();
        props.setProperty("user", "SYSDBA");
        props.setProperty("password", "masterkey");
        props.setProperty("encoding", charset);
        props.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_MS));
        props.setProperty("loginTimeout", String.valueOf(CONNECTION_TIMEOUT_MS / 1000));
        props.setProperty("connectTimeout", String.valueOf(CONNECTION_TIMEOUT_MS / 1000));
        // Desabilitar autocommit para controle explícito
        props.setProperty("useFirebirdAutocommit", "false");

        Connection conn = DriverManager.getConnection(url, props);
        conn.setAutoCommit(false);
        return conn;
    }

    private static String buildUrl(AppConfig config, String charset) {
        // Formato: jdbc:firebirdsql://host:port/path?encoding=CHARSET
        String host = config.ipServidor();
        int port = config.porta();
        String base = config.basePath();

        // Normalizar path do banco
        if (base == null || base.isBlank()) {
            base = "C:/TSD/Host/HOST.FDB";
            log.warn("BASEHOST nao configurado, usando padrao: {}", base);
        }

        return String.format(
            "jdbc:firebirdsql://%s:%d/%s?encoding=%s&useFirebirdAutocommit=false",
            host, port, base, charset
        );
    }

    /**
     * Testa se a conexão está acessível (ping rápido).
     */
    public static boolean testConnection(AppConfig config) {
        try (Connection conn = open(config)) {
            boolean valid = conn.isValid(5);
            if (valid) log.info("Teste de conexao Firebird: OK");
            return valid;
        } catch (SQLException e) {
            log.warn("Teste de conexao Firebird falhou: {}", e.getMessage());
            return false;
        }
    }
}
