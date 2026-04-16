package br.com.infoativa.fiscal.repository;

import br.com.infoativa.fiscal.domain.NfeItemRegistro;
import br.com.infoativa.fiscal.domain.Periodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio de itens de NFe (tabela NFE_ITENS).
 * Acessa a base Firebird diretamente.
 * A estrutura da tabela segue o padrao do ERP TSD:
 *   NFE_ITENS com mesma estrutura de NFCE_ITENS vinculado por ID_NFE -> NFE.ID
 */
public class NfeItemRepository {

    private static final Logger log = LoggerFactory.getLogger(NfeItemRepository.class);
    private final Connection conn;

    public NfeItemRepository(Connection conn) {
        this.conn = conn;
    }

    /**
     * Descobre dinamicamente as colunas da tabela NFE_ITENS
     * para garantir compatibilidade com diferentes versoes do ERP.
     */
    private boolean tableExists() {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, null, "NFE_ITENS", null);
            boolean exists = rs.next();
            rs.close();
            log.info("Tabela NFE_ITENS {}", exists ? "encontrada" : "NAO encontrada");
            return exists;
        } catch (SQLException e) {
            log.warn("Erro ao verificar tabela NFE_ITENS: {}", e.getMessage());
            return false;
        }
    }

    public List<NfeItemRegistro> findByNfeId(long nfeId) {
        List<NfeItemRegistro> registros = new ArrayList<>();
        if (!tableExists()) return registros;

        String sql = """
            SELECT i.ID, i.ID_PRODUTO, i.ID_NFE, i.CFOP, i.GTIN, i.ITEM,
                   i.QUANTIDADE, i.VALOR_UNITARIO, i.VALOR_TOTAL, i.TOTAL_ITEM,
                   i.BASE_ICMS, i.TAXA_ICMS, i.ICMS,
                   i.TAXA_PIS, i.PIS, i.TAXA_COFINS, i.COFINS,
                   i.DESCONTO, i.ACRESCIMO,
                   i.CST, i.CSOSN, i.CANCELADO,
                   i.ICMS_ST, i.ICMS_BC_ST, i.ICMS_TAXA_ST, i.ICMS_VALOR_ST,
                   i.IPI_BASE, i.IPI_TAXA, i.IPI_VALOR,
                   i.RED_BASE_ICMS, i.ORIGEM, i.DESCRICAO, i.NCM, i.UNIDADE
            FROM NFE_ITENS i
            WHERE i.ID_NFE = ?
            ORDER BY i.ITEM
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nfeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    registros.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.warn("Erro ao buscar itens NFE_ITENS para NFE {}: {}", nfeId, e.getMessage());
            // Tentar query simplificada caso alguma coluna nao exista
            registros = findByNfeIdSimple(nfeId);
        }
        return registros;
    }

    public List<NfeItemRegistro> findByPeriodo(Periodo periodo) {
        List<NfeItemRegistro> registros = new ArrayList<>();
        if (!tableExists()) return registros;

        String sql = """
            SELECT i.ID, i.ID_PRODUTO, i.ID_NFE, i.CFOP, i.GTIN, i.ITEM,
                   i.QUANTIDADE, i.VALOR_UNITARIO, i.VALOR_TOTAL, i.TOTAL_ITEM,
                   i.BASE_ICMS, i.TAXA_ICMS, i.ICMS,
                   i.TAXA_PIS, i.PIS, i.TAXA_COFINS, i.COFINS,
                   i.DESCONTO, i.ACRESCIMO,
                   i.CST, i.CSOSN, i.CANCELADO,
                   i.ICMS_ST, i.ICMS_BC_ST, i.ICMS_TAXA_ST, i.ICMS_VALOR_ST,
                   i.IPI_BASE, i.IPI_TAXA, i.IPI_VALOR,
                   i.RED_BASE_ICMS, i.ORIGEM, i.DESCRICAO, i.NCM, i.UNIDADE
            FROM NFE_ITENS i
            INNER JOIN NFE n ON i.ID_NFE = n.ID
            WHERE n.NFE_DATA_EMISSAO >= ? AND n.NFE_DATA_EMISSAO <= ?
            ORDER BY i.ID_NFE, i.ITEM
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    registros.add(mapRow(rs));
                }
            }
            log.info("Itens NFe no periodo: {}", registros.size());
        } catch (SQLException e) {
            log.warn("Erro ao buscar itens NFe por periodo: {}", e.getMessage());
        }
        return registros;
    }

    /**
     * Query simplificada caso a tabela tenha colunas diferentes
     */
    private List<NfeItemRegistro> findByNfeIdSimple(long nfeId) {
        List<NfeItemRegistro> registros = new ArrayList<>();
        String sql = "SELECT * FROM NFE_ITENS WHERE ID_NFE = ? ORDER BY ITEM";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nfeId);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    registros.add(new NfeItemRegistro(
                        getLong(rs, "ID"), getLong(rs, "ID_PRODUTO"), getLong(rs, "ID_NFE"),
                        getStr(rs, "CFOP"), getStr(rs, "GTIN"), getInt(rs, "ITEM"),
                        getBD(rs, "QUANTIDADE"), getBD(rs, "VALOR_UNITARIO"),
                        getBD(rs, "VALOR_TOTAL"), getBD(rs, "TOTAL_ITEM"),
                        getBD(rs, "BASE_ICMS"), getBD(rs, "TAXA_ICMS"), getBD(rs, "ICMS"),
                        getBD(rs, "TAXA_PIS"), getBD(rs, "PIS"),
                        getBD(rs, "TAXA_COFINS"), getBD(rs, "COFINS"),
                        getBD(rs, "DESCONTO"), getBD(rs, "ACRESCIMO"),
                        getStr(rs, "CST"), getStr(rs, "CSOSN"), getStr(rs, "CANCELADO"),
                        getBD(rs, "ICMS_ST"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, getStr(rs, "ORIGEM"),
                        getStr(rs, "DESCRICAO"), getStr(rs, "NCM"), getStr(rs, "UNIDADE")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Erro na query simplificada NFE_ITENS: {}", e.getMessage());
        }
        return registros;
    }

    private NfeItemRegistro mapRow(ResultSet rs) throws SQLException {
        return new NfeItemRegistro(
            rs.getLong("ID"), rs.getLong("ID_PRODUTO"), rs.getLong("ID_NFE"),
            getStr(rs, "CFOP"), getStr(rs, "GTIN"), rs.getInt("ITEM"),
            getBD(rs, "QUANTIDADE"), getBD(rs, "VALOR_UNITARIO"),
            getBD(rs, "VALOR_TOTAL"), getBD(rs, "TOTAL_ITEM"),
            getBD(rs, "BASE_ICMS"), getBD(rs, "TAXA_ICMS"), getBD(rs, "ICMS"),
            getBD(rs, "TAXA_PIS"), getBD(rs, "PIS"),
            getBD(rs, "TAXA_COFINS"), getBD(rs, "COFINS"),
            getBD(rs, "DESCONTO"), getBD(rs, "ACRESCIMO"),
            getStr(rs, "CST"), getStr(rs, "CSOSN"), getStr(rs, "CANCELADO"),
            getBD(rs, "ICMS_ST"), getBD(rs, "ICMS_BC_ST"),
            getBD(rs, "ICMS_TAXA_ST"), getBD(rs, "ICMS_VALOR_ST"),
            getBD(rs, "IPI_BASE"), getBD(rs, "IPI_TAXA"), getBD(rs, "IPI_VALOR"),
            getBD(rs, "RED_BASE_ICMS"), getStr(rs, "ORIGEM"),
            getStr(rs, "DESCRICAO"), getStr(rs, "NCM"), getStr(rs, "UNIDADE")
        );
    }

    private BigDecimal getBD(ResultSet rs, String c) {
        try { BigDecimal v = rs.getBigDecimal(c); return v != null ? v : BigDecimal.ZERO; }
        catch (SQLException e) { return BigDecimal.ZERO; }
    }
    private String getStr(ResultSet rs, String c) {
        try { String v = rs.getString(c); return v != null ? v.trim() : ""; }
        catch (SQLException e) { return ""; }
    }
    private long getLong(ResultSet rs, String c) {
        try { return rs.getLong(c); } catch (SQLException e) { return 0; }
    }
    private int getInt(ResultSet rs, String c) {
        try { return rs.getInt(c); } catch (SQLException e) { return 0; }
    }
}
