package br.com.infoativa.fiscal.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class FirebirdConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(FirebirdConnectionFactory.class);
    private final String jdbcUrl;

    /**
     * Firebird 2.5.9 connection string format:
     * jdbc:firebirdsql:host/port:path_to_fdb?encoding=WIN1252
     *
     * Example: jdbc:firebirdsql:127.0.0.1/3050:C:\TSD\Host\HOST.FDB?encoding=WIN1252
     */
    public FirebirdConnectionFactory(String ip, int porta, String basePath) {
        // Firebird 2.5 uses host/port:path format (NOT host:port/path)
        this.jdbcUrl = String.format("jdbc:firebirdsql:%s/%d:%s?encoding=WIN1252&charSet=WIN1252",
                ip, porta, basePath);
        log.info("JDBC URL: jdbc:firebirdsql:{}/%d:{}", ip, porta, basePath);
    }

    public Connection createConnection() throws SQLException {
        try {
            Class.forName("org.firebirdsql.jdbc.FBDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver Jaybird nao encontrado", e);
        }
        return DriverManager.getConnection(jdbcUrl, "SYSDBA", "masterkey");
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }
}
