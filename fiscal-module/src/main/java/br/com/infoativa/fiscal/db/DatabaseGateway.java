package br.com.infoativa.fiscal.db;

import br.com.infoativa.fiscal.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Gateway de banco de dados Firebird com try-with-resources support.
 * Mantém uma única conexão por instância (pool controlado).
 */
public final class DatabaseGateway implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseGateway.class);
    private Connection connection;
    private final AppConfig config;

    private DatabaseGateway(AppConfig config, Connection connection) {
        this.config = config;
        this.connection = connection;
    }

    public static DatabaseGateway open(AppConfig config) throws SQLException {
        Connection conn = FirebirdConnectionFactory.open(config);
        return new DatabaseGateway(config, conn);
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                log.info("Conexão fechada, reconectando...");
                connection = FirebirdConnectionFactory.open(config);
            }
        } catch (SQLException e) {
            log.error("Erro ao verificar/reabrir conexão", e);
        }
        return connection;
    }

    /** @deprecated use getConnection() */
    @Deprecated
    public Connection connection() { return getConnection(); }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) connection.close();
                log.debug("Conexão Firebird fechada");
            } catch (SQLException ignored) {}
        }
    }
}
