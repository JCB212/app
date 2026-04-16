package br.com.infoativa.fiscal.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Conexao com Firebird 2.5.x via Jaybird JDBC.
 *
 * Formato correto da URL JDBC para Jaybird:
 *   jdbc:firebirdsql://HOST:PORTA/CAMINHO_DO_BANCO
 *
 * Exemplo real:
 *   jdbc:firebirdsql://127.0.0.1:3050/C:/TSD/Host/HOST1.FDB
 *
 * Encoding: UTF8 (compativel com Jaybird + Firebird 2.5)
 * Charset do IBExpert: WIN1254, mas no Java usamos UTF8 para evitar erros.
 */
public class FirebirdConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(FirebirdConnectionFactory.class);
    private final String jdbcUrl;
    private final String usuario;
    private final String senha;

    public FirebirdConnectionFactory(String ip, int porta, String basePath) {
        this(ip, porta, basePath, "SYSDBA", "masterkey");
    }

    public FirebirdConnectionFactory(String ip, int porta, String basePath, String usuario, String senha) {
        // Normaliza o caminho: troca \ por / para compatibilidade
        String normalizedPath = basePath.replace("\\", "/");

        // Formato correto Jaybird: jdbc:firebirdsql://host:porta/caminho
        this.jdbcUrl = "jdbc:firebirdsql://" + ip + ":" + porta + "/" + normalizedPath;
        this.usuario = usuario;
        this.senha = senha;

        log.info("JDBC URL configurada: {}", this.jdbcUrl);
    }

    public Connection createConnection() throws SQLException {
        try {
            Class.forName("org.firebirdsql.jdbc.FBDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver Jaybird (Firebird JDBC) nao encontrado no classpath.", e);
        }

        Properties props = new Properties();
        props.setProperty("user", usuario);
        props.setProperty("password", senha);
        props.setProperty("encoding", "UTF8");
        props.setProperty("sqlDialect", "3");

        try {
            Connection conn = DriverManager.getConnection(jdbcUrl, props);
            log.info("Conexao Firebird estabelecida com sucesso!");
            return conn;
        } catch (SQLException e) {
            String msg = "Falha ao conectar no Firebird.\n"
                + "URL: " + jdbcUrl + "\n"
                + "Usuario: " + usuario + "\n"
                + "Erro: " + e.getMessage();
            log.error(msg);
            throw new SQLException(msg, e);
        }
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }
}
