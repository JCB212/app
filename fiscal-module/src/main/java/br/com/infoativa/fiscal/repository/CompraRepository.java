package br.com.infoativa.fiscal.repository;

import br.com.infoativa.fiscal.domain.CompraRegistro;
import br.com.infoativa.fiscal.domain.Periodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório de notas de compra / entradas com JOIN completo.
 * Inclui dados do fornecedor (CNPJ, Nome, IE, UF) para SPED e PDFs.
 */
public class CompraRepository {

    private static final Logger log = LoggerFactory.getLogger(CompraRepository.class);
    private final Connection conn;

    /** Query principal — itens de compra com cabeçalho da nota e fornecedor */
    private static final String SQL_ITENS = """
        SELECT
            d.ID                                        AS ID,
            d.ITEM                                      AS ITEM,
            COALESCE(d.ID_PRODUTO, 0)                   AS ID_PRODUTO,
            d.ID_NFE                                    AS ID_NFE,
            COALESCE(d.CODIGO_PRODUTO, '')              AS CODIGO_PRODUTO,
            COALESCE(d.CFOP, '1102')                    AS CFOP,
            COALESCE(d.QUANTIDADE, 0)                   AS QUANTIDADE,
            COALESCE(d.VALOR_UNITARIO, 0)               AS VALOR_UNITARIO,
            COALESCE(d.VALOR_COMPRA, d.VALOR_UNITARIO, 0) AS VALOR_COMPRA,
            COALESCE(d.TOTAL_ITEM, 0)                   AS TOTAL_ITEM,
            COALESCE(d.ICMS_VALOR, 0)                   AS ICMS_VALOR,
            COALESCE(d.ICMS_BC, d.ICMS_BASE, 0)         AS ICMS_BC,
            COALESCE(d.ICMS_TAXA, d.ICMS_ALIQ, 0)       AS ICMS_TAXA,
            COALESCE(d.ICMS_CST, '102')                 AS ICMS_CST,
            COALESCE(d.IPI_BASE, 0)                     AS IPI_BASE,
            COALESCE(d.IPI_TAXA, 0)                     AS IPI_TAXA,
            COALESCE(d.IPI_VALOR, 0)                    AS IPI_VALOR,
            COALESCE(d.PIS_TAXA, 0)                     AS PIS_TAXA,
            COALESCE(d.PIS_VALOR, 0)                    AS PIS_VALOR,
            COALESCE(d.COFINS_TAXA, 0)                  AS COFINS_TAXA,
            COALESCE(d.COFINS_VALOR, 0)                 AS COFINS_VALOR,
            COALESCE(d.DESCONTO, 0)                     AS DESCONTO,
            COALESCE(d.VALOR_FRETE, 0)                  AS VALOR_FRETE,
            COALESCE(d.VALOR_SEGURO, 0)                 AS VALOR_SEGURO,
            COALESCE(d.DESCRICAO, '')                   AS DESCRICAO,
            COALESCE(d.NCM, '')                         AS NCM,
            COALESCE(d.UNIDADE, 'UN')                   AS UNIDADE,
            COALESCE(d.ORIGEM, '0')                     AS ORIGEM,
            -- Cabeçalho da nota de compra
            nc.ID                                       AS NC_ID,
            COALESCE(nc.NOTA, nc.NFE_NUMERO, 0)         AS NOTA,
            COALESCE(nc.SERIE, nc.NFE_SERIE, 1)         AS SERIE,
            nc.DATA_EMISSAO                             AS DATA_EMISSAO,
            COALESCE(nc.NFE_CHAVE, nc.NFE_CHAVE_ACESSO, '') AS NFE_CHAVE,
            COALESCE(nc.NFE_STATUS, '')                 AS NFE_STATUS,
            COALESCE(nc.VALOR_TOTAL, nc.VALOR_NF, 0)    AS VALOR_TOTAL_NOTA,
            -- Dados do fornecedor
            COALESCE(f.CNPJ, f.CGC, '')                 AS CNPJ_FORNECEDOR,
            COALESCE(f.NOME, f.RAZAO_SOCIAL, '')         AS NOME_FORNECEDOR,
            COALESCE(f.INSCRICAO_ESTADUAL, f.IE, 'ISENTO') AS IE_FORNECEDOR,
            COALESCE(f.UF, '')                          AS UF_FORNECEDOR
        FROM NOTA_COMPRA_DETALHE d
        INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID
        LEFT JOIN CLIENTE f ON nc.ID_FORNECEDOR = f.ID
        WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ?
        ORDER BY nc.DATA_EMISSAO, nc.ID, d.ITEM
        """;

    /** Query fallback sem JOIN fornecedor */
    private static final String SQL_ITENS_SIMPLES = """
        SELECT
            d.ID, d.ITEM, COALESCE(d.ID_PRODUTO, 0) AS ID_PRODUTO, d.ID_NFE,
            COALESCE(d.CODIGO_PRODUTO, '')      AS CODIGO_PRODUTO,
            COALESCE(d.CFOP, '1102')            AS CFOP,
            COALESCE(d.QUANTIDADE, 0)           AS QUANTIDADE,
            COALESCE(d.VALOR_UNITARIO, 0)       AS VALOR_UNITARIO,
            COALESCE(d.VALOR_COMPRA, 0)         AS VALOR_COMPRA,
            COALESCE(d.TOTAL_ITEM, 0)           AS TOTAL_ITEM,
            COALESCE(d.ICMS_VALOR, 0)           AS ICMS_VALOR,
            COALESCE(d.ICMS_BC, 0)              AS ICMS_BC,
            COALESCE(d.ICMS_TAXA, 0)            AS ICMS_TAXA,
            COALESCE(d.ICMS_CST, '102')         AS ICMS_CST,
            COALESCE(d.IPI_BASE, 0)             AS IPI_BASE,
            COALESCE(d.IPI_TAXA, 0)             AS IPI_TAXA,
            COALESCE(d.IPI_VALOR, 0)            AS IPI_VALOR,
            COALESCE(d.PIS_TAXA, 0)             AS PIS_TAXA,
            COALESCE(d.PIS_VALOR, 0)            AS PIS_VALOR,
            COALESCE(d.COFINS_TAXA, 0)          AS COFINS_TAXA,
            COALESCE(d.COFINS_VALOR, 0)         AS COFINS_VALOR,
            COALESCE(d.DESCONTO, 0)             AS DESCONTO,
            COALESCE(d.VALOR_FRETE, 0)          AS VALOR_FRETE,
            COALESCE(d.VALOR_SEGURO, 0)         AS VALOR_SEGURO,
            COALESCE(d.DESCRICAO, '')           AS DESCRICAO,
            COALESCE(d.NCM, '')                 AS NCM,
            COALESCE(d.UNIDADE, 'UN')           AS UNIDADE,
            COALESCE(d.ORIGEM, '0')             AS ORIGEM,
            nc.ID                               AS NC_ID,
            COALESCE(nc.NOTA, 0)                AS NOTA,
            COALESCE(nc.SERIE, 1)               AS SERIE,
            nc.DATA_EMISSAO,
            COALESCE(nc.NFE_CHAVE, '')          AS NFE_CHAVE,
            COALESCE(nc.NFE_STATUS, '')         AS NFE_STATUS,
            COALESCE(nc.VALOR_TOTAL, 0)         AS VALOR_TOTAL_NOTA,
            ''  AS CNPJ_FORNECEDOR,
            ''  AS NOME_FORNECEDOR,
            'ISENTO' AS IE_FORNECEDOR,
            ''  AS UF_FORNECEDOR
        FROM NOTA_COMPRA_DETALHE d
        INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID
        WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ?
        ORDER BY nc.DATA_EMISSAO, nc.ID, d.ITEM
        """;

    public CompraRepository(Connection conn) {
        this.conn = conn;
    }

    // ── Consultas ─────────────────────────────────────────────────────────

    public List<CompraRegistro> findByPeriodo(Periodo periodo) {
        List<CompraRegistro> lista = new ArrayList<>();
        try {
            lista = execQuery(SQL_ITENS, periodo);
            log.info("Itens de compra com fornecedor: {} registros", lista.size());
        } catch (SQLException e) {
            log.warn("JOIN FORNECEDOR falhou ({}), usando query simples", e.getMessage());
            try {
                lista = execQuery(SQL_ITENS_SIMPLES, periodo);
                log.info("Itens de compra (sem fornecedor): {} registros", lista.size());
            } catch (SQLException ex) {
                log.error("Erro ao buscar compras no periodo {}", periodo.descricao(), ex);
            }
        }
        return lista;
    }

    public BigDecimal totalComprasPeriodo(Periodo periodo) {
        String sql = """
            SELECT COALESCE(SUM(d.TOTAL_ITEM), 0)
            FROM NOTA_COMPRA_DETALHE d
            INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID
            WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            log.error("Erro total compras", e);
        }
        return BigDecimal.ZERO;
    }

    /** Agrupamento por CST/CFOP para relatório */
    public List<Object[]> findGroupedByCstCfop(Periodo periodo) {
        String sql = """
            SELECT
                COALESCE(d.ICMS_CST, '102')             AS ICMS_CST,
                COALESCE(d.CFOP, '1102')                AS CFOP,
                COUNT(*)                                AS QTD,
                SUM(COALESCE(d.TOTAL_ITEM, 0))          AS TOTAL,
                SUM(COALESCE(d.ICMS_VALOR, 0))          AS TOTAL_ICMS,
                SUM(COALESCE(d.PIS_VALOR, 0))           AS TOTAL_PIS,
                SUM(COALESCE(d.COFINS_VALOR, 0))        AS TOTAL_COFINS
            FROM NOTA_COMPRA_DETALHE d
            INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID
            WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ?
            GROUP BY d.ICMS_CST, d.CFOP
            ORDER BY d.ICMS_CST, d.CFOP
            """;
        List<Object[]> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new Object[]{
                        rs.getString(1), rs.getString(2),
                        rs.getInt(3), rs.getBigDecimal(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7)
                    });
                }
            }
        } catch (SQLException e) {
            log.error("Erro agrupamento compras CST/CFOP", e);
        }
        return results;
    }

    // ── Internos ──────────────────────────────────────────────────────────

    private List<CompraRegistro> execQuery(String sql, Periodo periodo) throws SQLException {
        List<CompraRegistro> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
            }
        }
        return lista;
    }

    private CompraRegistro mapRow(ResultSet rs) throws SQLException {
        return new CompraRegistro(
            rs.getLong("ID"), rs.getInt("ITEM"),
            rs.getLong("ID_PRODUTO"), rs.getLong("ID_NFE"),
            str(rs, "CODIGO_PRODUTO"),
            str(rs, "CFOP"),
            getBD(rs, "QUANTIDADE"), getBD(rs, "VALOR_UNITARIO"),
            getBD(rs, "VALOR_COMPRA"), getBD(rs, "TOTAL_ITEM"),
            getBD(rs, "ICMS_VALOR"), getBD(rs, "ICMS_BC"), getBD(rs, "ICMS_TAXA"),
            str(rs, "ICMS_CST"),
            getBD(rs, "IPI_BASE"), getBD(rs, "IPI_TAXA"), getBD(rs, "IPI_VALOR"),
            getBD(rs, "PIS_TAXA"), getBD(rs, "PIS_VALOR"),
            getBD(rs, "COFINS_TAXA"), getBD(rs, "COFINS_VALOR"),
            getBD(rs, "DESCONTO"), getBD(rs, "VALOR_FRETE"), getBD(rs, "VALOR_SEGURO"),
            str(rs, "DESCRICAO"), str(rs, "NCM"), str(rs, "UNIDADE"), str(rs, "ORIGEM"),
            // Cabeçalho nota
            rs.getLong("NOTA"), rs.getInt("SERIE"),
            getLD(rs, "DATA_EMISSAO"),
            str(rs, "NFE_CHAVE"), str(rs, "NFE_STATUS"),
            getBD(rs, "VALOR_TOTAL_NOTA"),
            // Fornecedor
            str(rs, "CNPJ_FORNECEDOR"), str(rs, "NOME_FORNECEDOR"),
            str(rs, "IE_FORNECEDOR"),   str(rs, "UF_FORNECEDOR")
        );
    }

    private BigDecimal getBD(ResultSet rs, String c) throws SQLException {
        BigDecimal v = rs.getBigDecimal(c); return v != null ? v : BigDecimal.ZERO;
    }
    private LocalDate getLD(ResultSet rs, String c) throws SQLException {
        Date d = rs.getDate(c); return d != null ? d.toLocalDate() : null;
    }
    private String str(ResultSet rs, String c) {
        try { String v = rs.getString(c); return v != null ? v.trim() : ""; }
        catch (Exception e) { return ""; }
    }
}
