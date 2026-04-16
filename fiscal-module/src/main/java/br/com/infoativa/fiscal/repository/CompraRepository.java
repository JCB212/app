package br.com.infoativa.fiscal.repository;

import br.com.infoativa.fiscal.domain.CompraRegistro;
import br.com.infoativa.fiscal.domain.Periodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CompraRepository {

    private static final Logger log = LoggerFactory.getLogger(CompraRepository.class);
    private final Connection conn;

    public CompraRepository(Connection conn) {
        this.conn = conn;
    }

    public List<CompraRegistro> findByPeriodo(Periodo periodo) {
        List<CompraRegistro> registros = new ArrayList<>();
        String sql = """
            SELECT d.ID, d.ITEM, d.ID_PRODUTO, d.ID_NFE, d.CFOP,
                   d.QUANTIDADE, d.VALOR_UNITARIO, d.VALOR_COMPRA, d.TOTAL_ITEM,
                   d.ICMS_VALOR, d.ICMS_BC, d.ICMS_TAXA, d.ICMS_CST,
                   d.IPI_BASE, d.IPI_TAXA, d.IPI_VALOR,
                   d.PIS_TAXA, d.PIS_VALOR, d.COFINS_TAXA, d.COFINS_VALOR,
                   d.DESCONTO, d.VALOR_FRETE, d.VALOR_SEGURO,
                   d.DESCRICAO, d.NCM, d.UNIDADE, d.ORIGEM
            FROM NOTA_COMPRA_DETALHE d
            INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID
            WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ?
            ORDER BY d.ID_NFE, d.ITEM
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    registros.add(mapRow(rs));
                }
            }
            log.info("Registros de compra no periodo: {}", registros.size());
        } catch (SQLException e) {
            log.error("Erro ao buscar compras: {}", e.getMessage());
        }
        return registros;
    }

    public BigDecimal totalComprasPeriodo(Periodo periodo) {
        String sql = """
            SELECT COALESCE(SUM(d.TOTAL_ITEM),0) FROM NOTA_COMPRA_DETALHE d
            INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID
            WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBigDecimal(1);
        } catch (SQLException e) {
            log.error("Erro total compras: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    /**
     * Compras agrupadas por CST/CFOP
     */
    public List<Object[]> findGroupedByCstCfop(Periodo periodo) {
        List<Object[]> results = new ArrayList<>();
        String sql = """
            SELECT d.ICMS_CST, d.CFOP, COUNT(*) as QTD, SUM(d.TOTAL_ITEM) as TOTAL,
                   SUM(d.ICMS_VALOR) as TOTAL_ICMS, SUM(d.PIS_VALOR) as TOTAL_PIS,
                   SUM(d.COFINS_VALOR) as TOTAL_COFINS
            FROM NOTA_COMPRA_DETALHE d
            INNER JOIN NOTA_COMPRA nc ON d.ID_NFE = nc.ID
            WHERE nc.DATA_EMISSAO >= ? AND nc.DATA_EMISSAO <= ?
            GROUP BY d.ICMS_CST, d.CFOP
            ORDER BY d.ICMS_CST, d.CFOP
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new Object[]{
                        rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5),
                        rs.getBigDecimal(6), rs.getBigDecimal(7)
                    });
                }
            }
        } catch (SQLException e) {
            log.error("Erro CST/CFOP compras: {}", e.getMessage());
        }
        return results;
    }

    private CompraRegistro mapRow(ResultSet rs) throws SQLException {
        return new CompraRegistro(
            rs.getLong("ID"), rs.getInt("ITEM"), rs.getLong("ID_PRODUTO"),
            rs.getLong("ID_NFE"), rs.getString("CFOP") != null ? rs.getString("CFOP").trim() : "",
            getBD(rs,"QUANTIDADE"), getBD(rs,"VALOR_UNITARIO"),
            getBD(rs,"VALOR_COMPRA"), getBD(rs,"TOTAL_ITEM"),
            getBD(rs,"ICMS_VALOR"), getBD(rs,"ICMS_BC"), getBD(rs,"ICMS_TAXA"),
            rs.getString("ICMS_CST") != null ? rs.getString("ICMS_CST").trim() : "",
            getBD(rs,"IPI_BASE"), getBD(rs,"IPI_TAXA"), getBD(rs,"IPI_VALOR"),
            getBD(rs,"PIS_TAXA"), getBD(rs,"PIS_VALOR"),
            getBD(rs,"COFINS_TAXA"), getBD(rs,"COFINS_VALOR"),
            getBD(rs,"DESCONTO"), getBD(rs,"VALOR_FRETE"), getBD(rs,"VALOR_SEGURO"),
            rs.getString("DESCRICAO") != null ? rs.getString("DESCRICAO").trim() : "",
            rs.getString("NCM") != null ? rs.getString("NCM").trim() : "",
            rs.getString("UNIDADE") != null ? rs.getString("UNIDADE").trim() : "",
            rs.getString("ORIGEM") != null ? rs.getString("ORIGEM").trim() : ""
        );
    }

    private BigDecimal getBD(ResultSet rs, String c) throws SQLException {
        BigDecimal v = rs.getBigDecimal(c); return v != null ? v : BigDecimal.ZERO;
    }
}
