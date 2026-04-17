package br.com.infoativa.fiscal.repository;

import br.com.infoativa.fiscal.domain.NfeRegistro;
import br.com.infoativa.fiscal.domain.Periodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório de NF-e com JOIN completo ao cadastro de clientes.
 *
 * Compatibilidade: tenta JOIN com CLIENTE, CLIENTES ou CADASTRO.
 * Se o JOIN falhar (tabela inexistente), executa query sem JOIN
 * retornando campos de cliente como vazios.
 */
public class NfeRepository {

    private static final Logger log = LoggerFactory.getLogger(NfeRepository.class);
    private final Connection conn;

    // Query principal com JOIN ao cadastro de clientes (Firebird / ERP TSD)
    private static final String SQL_COM_CLIENTE = """
        SELECT
            n.ID,
            n.ID_CLIENTE,
            n.NFE_NUMERO,
            COALESCE(n.NFE_SERIE, 1)                AS NFE_SERIE,
            COALESCE(n.NFE_MODELO, 55)              AS NFE_MODELO,
            n.DATA_VENDA,
            n.NFE_DATA_EMISSAO,
            n.NFE_DH_EMISSAO,
            n.NFE_CHAVE_ACESSO,
            n.NFE_PROTOCOLO,
            n.NFE_STATUS,
            COALESCE(n.CFOP, '5102')                AS CFOP,
            COALESCE(n.ENTRADA_SAIDA, 'S')          AS ENTRADA_SAIDA,
            COALESCE(n.VALOR_FINAL, 0)              AS VALOR_FINAL,
            COALESCE(n.TOTAL_PRODUTOS, 0)           AS TOTAL_PRODUTOS,
            COALESCE(n.DESCONTO, 0)                 AS DESCONTO,
            COALESCE(n.VALOR_BASE_ICMS, 0)          AS VALOR_BASE_ICMS,
            COALESCE(n.VALOR_ICMS, 0)               AS VALOR_ICMS,
            COALESCE(n.VALOR_BASE_ICMS_ST, 0)       AS VALOR_BASE_ICMS_ST,
            COALESCE(n.VALOR_ICMS_ST, 0)            AS VALOR_ICMS_ST,
            COALESCE(n.VALOR_IPI, 0)                AS VALOR_IPI,
            COALESCE(n.VALOR_PIS, 0)                AS VALOR_PIS,
            COALESCE(n.VALOR_COFINS, 0)             AS VALOR_COFINS,
            COALESCE(n.VALOR_FRETE, 0)              AS VALOR_FRETE,
            COALESCE(n.VALOR_SEGURO, 0)             AS VALOR_SEGURO,
            COALESCE(n.CANCELADO, 'N')              AS CANCELADO,
            n.TIPO_OPERACAO,
            n.REGIME_TRIBUTARIO,
            -- Dados do cliente (JOIN)
            COALESCE(c.NOME, c.RAZAO_SOCIAL, '')    AS NOME_CLIENTE,
            COALESCE(c.CNPJ, '')                    AS CNPJ_CLIENTE,
            COALESCE(c.CPF, '')                     AS CPF_CLIENTE,
            COALESCE(c.INSCRICAO_ESTADUAL, c.IE, 'ISENTO') AS IE_CLIENTE,
            COALESCE(c.UF, '')                      AS UF_CLIENTE,
            COALESCE(c.MUNICIPIO, c.CIDADE, '')     AS MUNICIPIO_CLIENTE
        FROM NFE n
        LEFT JOIN CLIENTE c ON n.ID_CLIENTE = c.ID
        WHERE n.NFE_DATA_EMISSAO >= ? AND n.NFE_DATA_EMISSAO <= ?
        ORDER BY n.NFE_DATA_EMISSAO, n.NFE_NUMERO
        """;

    // Query fallback sem JOIN (caso tabela CLIENTE tenha nome diferente)
    private static final String SQL_SEM_CLIENTE = """
        SELECT
            ID,
            ID_CLIENTE,
            NFE_NUMERO,
            COALESCE(NFE_SERIE, 1)                  AS NFE_SERIE,
            COALESCE(NFE_MODELO, 55)                AS NFE_MODELO,
            DATA_VENDA,
            NFE_DATA_EMISSAO,
            NFE_DH_EMISSAO,
            NFE_CHAVE_ACESSO,
            NFE_PROTOCOLO,
            NFE_STATUS,
            COALESCE(CFOP, '5102')                  AS CFOP,
            COALESCE(ENTRADA_SAIDA, 'S')            AS ENTRADA_SAIDA,
            COALESCE(VALOR_FINAL, 0)                AS VALOR_FINAL,
            COALESCE(TOTAL_PRODUTOS, 0)             AS TOTAL_PRODUTOS,
            COALESCE(DESCONTO, 0)                   AS DESCONTO,
            COALESCE(VALOR_BASE_ICMS, 0)            AS VALOR_BASE_ICMS,
            COALESCE(VALOR_ICMS, 0)                 AS VALOR_ICMS,
            COALESCE(VALOR_BASE_ICMS_ST, 0)         AS VALOR_BASE_ICMS_ST,
            COALESCE(VALOR_ICMS_ST, 0)              AS VALOR_ICMS_ST,
            COALESCE(VALOR_IPI, 0)                  AS VALOR_IPI,
            COALESCE(VALOR_PIS, 0)                  AS VALOR_PIS,
            COALESCE(VALOR_COFINS, 0)               AS VALOR_COFINS,
            COALESCE(VALOR_FRETE, 0)                AS VALOR_FRETE,
            COALESCE(VALOR_SEGURO, 0)               AS VALOR_SEGURO,
            COALESCE(CANCELADO, 'N')                AS CANCELADO,
            TIPO_OPERACAO,
            REGIME_TRIBUTARIO
        FROM NFE
        WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ?
        ORDER BY NFE_DATA_EMISSAO, NFE_NUMERO
        """;

    public NfeRepository(Connection conn) {
        this.conn = conn;
    }

    // ── Consultas principais ──────────────────────────────────────────────

    public List<NfeRegistro> findByPeriodo(Periodo periodo) {
        List<NfeRegistro> registros = new ArrayList<>();
        try {
            registros = executarQuery(SQL_COM_CLIENTE, periodo, true);
            log.info("NFe com JOIN cliente: {} registros", registros.size());
        } catch (SQLException e) {
            log.warn("JOIN CLIENTE falhou ({}), tentando sem JOIN...", e.getMessage());
            try {
                registros = executarQuery(SQL_SEM_CLIENTE, periodo, false);
                log.info("NFe sem JOIN: {} registros", registros.size());
            } catch (SQLException ex) {
                log.error("Erro ao buscar NFe no periodo {}", periodo.descricao(), ex);
            }
        }
        return registros;
    }

    /** Busca uma NF-e específica pela chave de acesso */
    public NfeRegistro findByChave(String chaveAcesso) {
        String sql = SQL_COM_CLIENTE.replace(
            "WHERE n.NFE_DATA_EMISSAO >= ? AND n.NFE_DATA_EMISSAO <= ?",
            "WHERE n.NFE_CHAVE_ACESSO = ?"
        ).replace("ORDER BY n.NFE_DATA_EMISSAO, n.NFE_NUMERO", "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, chaveAcesso);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs, true);
            }
        } catch (Exception e) {
            log.error("Erro ao buscar NFe por chave {}", chaveAcesso, e);
        }
        return null;
    }

    /** Total de vendas no período (sem canceladas) */
    public BigDecimal totalVendasPeriodo(Periodo periodo) {
        String sql = """
            SELECT COALESCE(SUM(VALOR_FINAL), 0)
            FROM NFE
            WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ?
              AND COALESCE(CANCELADO, 'N') <> 'S'
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return getBD(rs, 1);
            }
        } catch (SQLException e) {
            log.error("Erro total vendas NFe", e);
        }
        return BigDecimal.ZERO;
    }

    /** Agrupamento por CFOP para relatório CST/CFOP e SPED */
    public List<Object[]> findAgrupandoPorCfop(Periodo periodo) {
        String sql = """
            SELECT
                COALESCE(CFOP, '5102')              AS CFOP,
                COUNT(*)                            AS QTD,
                SUM(COALESCE(TOTAL_PRODUTOS, 0))    AS VL_OPR,
                SUM(COALESCE(VALOR_BASE_ICMS, 0))   AS VL_BC_ICMS,
                SUM(COALESCE(VALOR_ICMS, 0))        AS VL_ICMS,
                SUM(COALESCE(VALOR_BASE_ICMS_ST, 0)) AS VL_BC_ST,
                SUM(COALESCE(VALOR_ICMS_ST, 0))     AS VL_ICMS_ST,
                SUM(COALESCE(VALOR_IPI, 0))         AS VL_IPI,
                SUM(COALESCE(VALOR_PIS, 0))         AS VL_PIS,
                SUM(COALESCE(VALOR_COFINS, 0))      AS VL_COFINS
            FROM NFE
            WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ?
              AND COALESCE(CANCELADO, 'N') <> 'S'
            GROUP BY CFOP
            ORDER BY CFOP
            """;
        List<Object[]> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new Object[]{
                        rs.getString("CFOP"),
                        rs.getInt("QTD"),
                        getBD(rs, "VL_OPR"),
                        getBD(rs, "VL_BC_ICMS"),
                        getBD(rs, "VL_ICMS"),
                        getBD(rs, "VL_BC_ST"),
                        getBD(rs, "VL_ICMS_ST"),
                        getBD(rs, "VL_IPI"),
                        getBD(rs, "VL_PIS"),
                        getBD(rs, "VL_COFINS")
                    });
                }
            }
        } catch (SQLException e) {
            log.error("Erro agrupamento NFe por CFOP", e);
        }
        return results;
    }

    // ── Internos ──────────────────────────────────────────────────────────

    private List<NfeRegistro> executarQuery(String sql, Periodo periodo, boolean comCliente)
            throws SQLException {
        List<NfeRegistro> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs, comCliente));
            }
        }
        return lista;
    }

    private NfeRegistro mapRow(ResultSet rs, boolean comCliente) throws SQLException {
        return new NfeRegistro(
            rs.getLong("ID"),
            rs.getLong("ID_CLIENTE"),
            rs.getInt("NFE_NUMERO"),
            rs.getInt("NFE_SERIE"),
            rs.getInt("NFE_MODELO"),
            getLD(rs, "DATA_VENDA"),
            getLD(rs, "NFE_DATA_EMISSAO"),
            getLDT(rs, "NFE_DH_EMISSAO"),
            str(rs, "NFE_CHAVE_ACESSO"),
            str(rs, "NFE_PROTOCOLO"),
            str(rs, "NFE_STATUS"),
            str(rs, "CFOP"),
            str(rs, "ENTRADA_SAIDA"),
            getBD(rs, "VALOR_FINAL"),
            getBD(rs, "TOTAL_PRODUTOS"),
            getBD(rs, "DESCONTO"),
            getBD(rs, "VALOR_BASE_ICMS"),
            getBD(rs, "VALOR_ICMS"),
            getBD(rs, "VALOR_BASE_ICMS_ST"),
            getBD(rs, "VALOR_ICMS_ST"),
            getBD(rs, "VALOR_IPI"),
            getBD(rs, "VALOR_PIS"),
            getBD(rs, "VALOR_COFINS"),
            getBD(rs, "VALOR_FRETE"),
            getBD(rs, "VALOR_SEGURO"),
            str(rs, "CANCELADO"),
            str(rs, "TIPO_OPERACAO"),
            str(rs, "REGIME_TRIBUTARIO"),
            // Campos de cliente
            comCliente ? str(rs, "NOME_CLIENTE")      : "",
            comCliente ? str(rs, "CNPJ_CLIENTE")      : "",
            comCliente ? str(rs, "CPF_CLIENTE")       : "",
            comCliente ? str(rs, "IE_CLIENTE")        : "ISENTO",
            comCliente ? str(rs, "UF_CLIENTE")        : "",
            comCliente ? str(rs, "MUNICIPIO_CLIENTE") : ""
        );
    }

    private BigDecimal getBD(ResultSet rs, String c) throws SQLException {
        BigDecimal v = rs.getBigDecimal(c); return v != null ? v : BigDecimal.ZERO;
    }
    private BigDecimal getBD(ResultSet rs, int i) throws SQLException {
        BigDecimal v = rs.getBigDecimal(i); return v != null ? v : BigDecimal.ZERO;
    }
    private LocalDate getLD(ResultSet rs, String c) throws SQLException {
        Date d = rs.getDate(c); return d != null ? d.toLocalDate() : null;
    }
    private LocalDateTime getLDT(ResultSet rs, String c) throws SQLException {
        Timestamp t = rs.getTimestamp(c); return t != null ? t.toLocalDateTime() : null;
    }
    private String str(ResultSet rs, String c) {
        try { String v = rs.getString(c); return v != null ? v.trim() : ""; }
        catch (Exception e) { return ""; }
    }
}
