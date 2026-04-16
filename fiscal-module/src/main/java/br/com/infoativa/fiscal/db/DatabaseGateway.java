package br.com.infoativa.fiscal.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseGateway implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseGateway.class);
    private final FirebirdConnectionFactory factory;
    private Connection connection;

    public DatabaseGateway(FirebirdConnectionFactory factory) {
        this.factory = factory;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = factory.createConnection();
            log.info("Conexao com Firebird estabelecida");
        }
        return connection;
    }

    public boolean testConnection() {
        try {
            Connection conn = getConnection();
            boolean valid = conn != null && !conn.isClosed();
            if (valid) {
                conn.createStatement().execute("SELECT 1 FROM RDB$DATABASE");
            }
            log.info("Teste de conexao: {}", valid ? "OK" : "FALHOU");
            return valid;
        } catch (SQLException e) {
            log.error("Falha no teste de conexao: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("Conexao com Firebird fechada");
            } catch (SQLException e) {
                log.error("Erro ao fechar conexao: {}", e.getMessage());
            }
        }
    }
}
