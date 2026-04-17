package br.com.infoativa.fiscal.repository;

import br.com.infoativa.fiscal.domain.NfceRegistro;
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
 * Repositório de NFC-e com campos de cliente separados (CNPJ / CPF / IE).
 */
public class NfceRepository {

    private static final Logger log = LoggerFactory.getLogger(NfceRepository.class);
    private final Connection conn;

    private static final String SQL_COM_CLIENTE = """
        SELECT
            n.ID,
            n.ID_CLIENTE,
            n.NFCE_NUMERO,
            COALESCE(n.NFCE_SERIE, 1)              AS NFCE_SERIE,
            COALESCE(n.NFCE_MODELO, 65)             AS NFCE_MODELO,
            n.NFCE_DATA_EMISSAO,
            n.NFCE_DH_EMISSAO,
            n.NFCE_CHAVE_ACESSO,
            n.NFCE_PROTOCOLO,
            n.NFCE_STATUS,
            COALESCE(n.CFOP, '5405')               AS CFOP,
            n.NFCE_NATUREZA_OPERACAO,
            COALESCE(n.VALOR_FINAL, 0)             AS VALOR_FINAL,
            COALESCE(n.TOTAL_PRODUTOS, 0)          AS TOTAL_PRODUTOS,
            COALESCE(n.TOTAL_DOCUMENTO, 0)         AS TOTAL_DOCUMENTO,
            COALESCE(n.BASE_ICMS, 0)               AS BASE_ICMS,
            COALESCE(n.ICMS, 0)                    AS ICMS,
            COALESCE(n.ICMS_OUTRAS, 0)             AS ICMS_OUTRAS,
            COALESCE(n.PIS, 0)                     AS PIS,
            COALESCE(n.COFINS, 0)                  AS COFINS,
            COALESCE(n.DESCONTO, 0)                AS DESCONTO,
            COALESCE(n.ACRESCIMO, 0)               AS ACRESCIMO,
            COALESCE(n.CUPOM_CANCELADO, 'N')       AS CUPOM_CANCELADO,
            n.TIPO_OPERACAO,
            -- Dados do cliente (prioridade: cadastro JOIN, depois campos inline da NFCe)
            COALESCE(c.NOME, c.RAZAO_SOCIAL, n.NOME_CLIENTE, '')       AS NOME_CLIENTE,
            COALESCE(c.CNPJ, n.CPF_CNPJ_CLIENTE, '')                  AS CNPJ_CLIENTE,
            COALESCE(c.CPF,
                CASE WHEN LENGTH(TRIM(COALESCE(n.CPF_CNPJ_CLIENTE,''))) = 11
                     THEN n.CPF_CNPJ_CLIENTE ELSE '' END, '')          AS CPF_CLIENTE,
            COALESCE(c.INSCRICAO_ESTADUAL, c.IE, 'ISENTO')             AS IE_CLIENTE
        FROM NFCE n
        LEFT JOIN CLIENTE c ON n.ID_CLIENTE = c.ID
        WHERE n.NFCE_DATA_EMISSAO >= ? AND n.NFCE_DATA_EMISSAO <= ?
        ORDER BY n.NFCE_DATA_EMISSAO, n.NFCE_NUMERO
        """;

    private static final String SQL_SEM_CLIENTE = """
        SELECT
            ID,
            ID_CLIENTE,
            NFCE_NUMERO,
            COALESCE(NFCE_SERIE, 1)                AS NFCE_SERIE,
            COALESCE(NFCE_MODELO, 65)              AS NFCE_MODELO,
            NFCE_DATA_EMISSAO,
            NFCE_DH_EMISSAO,
            NFCE_CHAVE_ACESSO,
            NFCE_PROTOCOLO,
            NFCE_STATUS,
            COALESCE(CFOP, '5405')                 AS CFOP,
            NFCE_NATUREZA_OPERACAO,
            COALESCE(VALOR_FINAL, 0)               AS VALOR_FINAL,
            COALESCE(TOTAL_PRODUTOS, 0)            AS TOTAL_PRODUTOS,
            COALESCE(TOTAL_DOCUMENTO, 0)           AS TOTAL_DOCUMENTO,
            COALESCE(BASE_ICMS, 0)                 AS BASE_ICMS,
            COALESCE(ICMS, 0)                      AS ICMS,
            COALESCE(ICMS_OUTRAS, 0)               AS ICMS_OUTRAS,
            COALESCE(PIS, 0)                       AS PIS,
            COALESCE(COFINS, 0)                    AS COFINS,
            COALESCE(DESCONTO, 0)                  AS DESCONTO,
            COALESCE(ACRESCIMO, 0)                 AS ACRESCIMO,
            COALESCE(CUPOM_CANCELADO, 'N')         AS CUPOM_CANCELADO,
            TIPO_OPERACAO,
            COALESCE(NOME_CLIENTE, '')             AS NOME_CLIENTE,
            ''                                     AS CNPJ_CLIENTE,
            COALESCE(CPF_CNPJ_CLIENTE, '')         AS CPF_CLIENTE,
            'ISENTO'                               AS IE_CLIENTE
        FROM NFCE
        WHERE NFCE_DATA_EMISSAO >= ? AND NFCE_DATA_EMISSAO <= ?
        ORDER BY NFCE_DATA_EMISSAO, NFCE_NUMERO
        """;

    public NfceRepository(Connection conn) {
        this.conn = conn;
    }

    public List<NfceRegistro> findByPeriodo(Periodo periodo) {
        List<NfceRegistro> lista = new ArrayList<>();
        try {
            lista = execQuery(SQL_COM_CLIENTE, periodo, true);
            log.info("NFCe com JOIN cliente: {} registros", lista.size());
        } catch (SQLException e) {
            log.warn("JOIN CLIENTE (NFCe) falhou: {}, tentando sem JOIN", e.getMessage());
            try {
                lista = execQuery(SQL_SEM_CLIENTE, periodo, false);
                log.info("NFCe sem JOIN: {} registros", lista.size());
            } catch (SQLException ex) {
                log.error("Erro ao buscar NFCe no periodo {}", periodo.descricao(), ex);
            }
        }
        return lista;
    }

    public BigDecimal totalVendasPeriodo(Periodo periodo) {
        String sql = """
            SELECT COALESCE(SUM(VALOR_FINAL), 0)
            FROM NFCE
            WHERE NFCE_DATA_EMISSAO >= ? AND NFCE_DATA_EMISSAO <= ?
              AND COALESCE(CUPOM_CANCELADO, 'N') <> 'S'
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            log.error("Erro total vendas NFCe", e);
        }
        return BigDecimal.ZERO;
    }

    private List<NfceRegistro> execQuery(String sql, Periodo periodo, boolean comCliente)
            throws SQLException {
        List<NfceRegistro> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs, comCliente));
            }
        }
        return lista;
    }

    private NfceRegistro mapRow(ResultSet rs, boolean comCliente) throws SQLException {
        return new NfceRegistro(
            rs.getLong("ID"),
            rs.getLong("ID_CLIENTE"),
            rs.getInt("NFCE_NUMERO"),
            rs.getInt("NFCE_SERIE"),
            rs.getInt("NFCE_MODELO"),
            getLD(rs, "NFCE_DATA_EMISSAO"),
            getLDT(rs, "NFCE_DH_EMISSAO"),
            str(rs, "NFCE_CHAVE_ACESSO"),
            str(rs, "NFCE_PROTOCOLO"),
            str(rs, "NFCE_STATUS"),
            str(rs, "CFOP"),
            str(rs, "NFCE_NATUREZA_OPERACAO"),
            getBD(rs, "VALOR_FINAL"),
            getBD(rs, "TOTAL_PRODUTOS"),
            getBD(rs, "TOTAL_DOCUMENTO"),
            getBD(rs, "BASE_ICMS"),
            getBD(rs, "ICMS"),
            getBD(rs, "ICMS_OUTRAS"),
            getBD(rs, "PIS"),
            getBD(rs, "COFINS"),
            getBD(rs, "DESCONTO"),
            getBD(rs, "ACRESCIMO"),
            str(rs, "CUPOM_CANCELADO"),
            str(rs, "TIPO_OPERACAO"),
            str(rs, "NOME_CLIENTE"),
            str(rs, "CNPJ_CLIENTE"),
            str(rs, "CPF_CLIENTE"),
            str(rs, "IE_CLIENTE")
        );
    }

    private BigDecimal getBD(ResultSet rs, String c) throws SQLException {
        BigDecimal v = rs.getBigDecimal(c); return v != null ? v : BigDecimal.ZERO;
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
