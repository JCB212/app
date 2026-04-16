package br.com.infoativa.fiscal.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class FirebirdConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(FirebirdConnectionFactory.class);
    private final String jdbcUrl;

    public FirebirdConnectionFactory(String ip, int porta, String basePath) {
        this.jdbcUrl = String.format("jdbc:firebirdsql://%s:%d/%s?encoding=WIN1252&charSet=WIN1252",
                ip, porta, basePath);
        log.info("JDBC URL configurada: jdbc:firebirdsql://{}:{}/{}",ip, porta, basePath);
    }

    public Connection createConnection() throws SQLException {
        try {
            Class.forName("org.firebirdsql.jdbc.FBDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver Jaybird nao encontrado", e);
        }
        return DriverManager.getConnection(jdbcUrl, "SYSDBA", "masterkey");
    }
}
