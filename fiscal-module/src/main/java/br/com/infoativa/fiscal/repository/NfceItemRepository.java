package br.com.infoativa.fiscal.repository;

import br.com.infoativa.fiscal.domain.NfceItemRegistro;
import br.com.infoativa.fiscal.domain.Periodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NfceItemRepository {

    private static final Logger log = LoggerFactory.getLogger(NfceItemRepository.class);
    private final Connection conn;

    public NfceItemRepository(Connection conn) {
        this.conn = conn;
    }

    public List<NfceItemRegistro> findByPeriodo(Periodo periodo) {
        List<NfceItemRegistro> registros = new ArrayList<>();
        String sql = """
            SELECT i.ID, i.ID_PRODUTO, i.ID_NFCE, i.CFOP, i.GTIN, i.ITEM,
                   i.QUANTIDADE, i.VALOR_UNITARIO, i.VALOR_TOTAL, i.TOTAL_ITEM,
                   i.BASE_ICMS, i.TAXA_ICMS, i.ICMS,
                   i.TAXA_PIS, i.PIS, i.TAXA_COFINS, i.COFINS,
                   i.DESCONTO, i.ACRESCIMO,
                   i.CST, i.CSOSN, i.CANCELADO,
                   i.ICMS_MONO_RET_VALOR, i.ICMS_MONO_RET_TAXA, i.ICMS_MONO_RET_QTDE,
                   i.RED_BASE_ICMS
            FROM NFCE_ITENS i
            INNER JOIN NFCE n ON i.ID_NFCE = n.ID
            WHERE n.NFCE_DATA_EMISSAO >= ? AND n.NFCE_DATA_EMISSAO <= ?
              AND (n.CUPOM_CANCELADO = 'N' OR n.CUPOM_CANCELADO IS NULL)
            ORDER BY i.ID_NFCE, i.ITEM
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    registros.add(mapRow(rs));
                }
            }
            log.info("Itens NFCe no periodo: {}", registros.size());
        } catch (SQLException e) {
            log.error("Erro ao buscar itens NFCe: {}", e.getMessage());
        }
        return registros;
    }

    /**
     * Itens monofasicos - CST 02, 04, 15 ou com ICMS_MONO_RET_VALOR > 0
     */
    public List<NfceItemRegistro> findMonofasicos(Periodo periodo) {
        List<NfceItemRegistro> registros = new ArrayList<>();
        String sql = """
            SELECT i.ID, i.ID_PRODUTO, i.ID_NFCE, i.CFOP, i.GTIN, i.ITEM,
                   i.QUANTIDADE, i.VALOR_UNITARIO, i.VALOR_TOTAL, i.TOTAL_ITEM,
                   i.BASE_ICMS, i.TAXA_ICMS, i.ICMS,
                   i.TAXA_PIS, i.PIS, i.TAXA_COFINS, i.COFINS,
                   i.DESCONTO, i.ACRESCIMO,
                   i.CST, i.CSOSN, i.CANCELADO,
                   i.ICMS_MONO_RET_VALOR, i.ICMS_MONO_RET_TAXA, i.ICMS_MONO_RET_QTDE,
                   i.RED_BASE_ICMS
            FROM NFCE_ITENS i
            INNER JOIN NFCE n ON i.ID_NFCE = n.ID
            WHERE n.NFCE_DATA_EMISSAO >= ? AND n.NFCE_DATA_EMISSAO <= ?
              AND (n.CUPOM_CANCELADO = 'N' OR n.CUPOM_CANCELADO IS NULL)
              AND (i.CST IN ('02','04','15') OR i.ICMS_MONO_RET_VALOR > 0 OR i.CSOSN = '500')
            ORDER BY i.ID_NFCE, i.ITEM
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    registros.add(mapRow(rs));
                }
            }
            log.info("Itens monofasicos no periodo: {}", registros.size());
        } catch (SQLException e) {
            log.error("Erro ao buscar monofasicos: {}", e.getMessage());
        }
        return registros;
    }

    private NfceItemRegistro mapRow(ResultSet rs) throws SQLException {
        return new NfceItemRegistro(
            rs.getLong("ID"), rs.getLong("ID_PRODUTO"), rs.getLong("ID_NFCE"),
            safe(rs,"CFOP"), safe(rs,"GTIN"), rs.getInt("ITEM"),
            getBD(rs,"QUANTIDADE"), getBD(rs,"VALOR_UNITARIO"),
            getBD(rs,"VALOR_TOTAL"), getBD(rs,"TOTAL_ITEM"),
            getBD(rs,"BASE_ICMS"), getBD(rs,"TAXA_ICMS"), getBD(rs,"ICMS"),
            getBD(rs,"TAXA_PIS"), getBD(rs,"PIS"),
            getBD(rs,"TAXA_COFINS"), getBD(rs,"COFINS"),
            getBD(rs,"DESCONTO"), getBD(rs,"ACRESCIMO"),
            safe(rs,"CST"), safe(rs,"CSOSN"), safe(rs,"CANCELADO"),
            getBD(rs,"ICMS_MONO_RET_VALOR"), getBD(rs,"ICMS_MONO_RET_TAXA"),
            getBD(rs,"ICMS_MONO_RET_QTDE"), getBD(rs,"RED_BASE_ICMS")
        );
    }

    private BigDecimal getBD(ResultSet rs, String c) throws SQLException {
        BigDecimal v = rs.getBigDecimal(c); return v != null ? v : BigDecimal.ZERO;
    }
    private String safe(ResultSet rs, String c) throws SQLException {
        String s = rs.getString(c); return s != null ? s.trim() : "";
    }
}
