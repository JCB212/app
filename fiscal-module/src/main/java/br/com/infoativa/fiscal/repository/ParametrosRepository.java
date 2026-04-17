package br.com.infoativa.fiscal.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Busca parâmetros da empresa no banco Firebird (tabela PARAMETROS ou EMPRESA).
 */
public class ParametrosRepository {

    private static final Logger log = LoggerFactory.getLogger(ParametrosRepository.class);
    private final Connection connection;

    public ParametrosRepository(Connection connection) {
        this.connection = connection;
    }

    public String getNomeEmpresa() {
        for (String sql : new String[]{
            "SELECT NOME_FANTASIA FROM EMPRESA ROWS 1",
            "SELECT RAZAO_SOCIAL FROM EMPRESA ROWS 1",
            "SELECT NOME FROM PARAMETROS ROWS 1",
            "SELECT NOME_EMPRESA FROM PARAMETROS ROWS 1"
        }) {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && !v.isBlank()) return v.trim();
                }
            } catch (Exception ignored) {}
        }
        return "Empresa";
    }

    public String getCnpj() {
        for (String sql : new String[]{
            "SELECT CNPJ FROM EMPRESA ROWS 1",
            "SELECT CNPJ FROM PARAMETROS ROWS 1",
            "SELECT CGC FROM EMPRESA ROWS 1"
        }) {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && !v.isBlank()) return v.trim().replaceAll("[^0-9]", "");
                }
            } catch (Exception ignored) {}
        }
        return "";
    }
}
