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

public class NfeRepository {

    private static final Logger log = LoggerFactory.getLogger(NfeRepository.class);
    private final Connection conn;

    public NfeRepository(Connection conn) {
        this.conn = conn;
    }

    public List<NfeRegistro> findByPeriodo(Periodo periodo) {
        List<NfeRegistro> registros = new ArrayList<>();
        String sql = """
            SELECT ID, ID_CLIENTE, NFE_NUMERO, NFE_SERIE, NFE_MODELO,
                   DATA_VENDA, NFE_DATA_EMISSAO, NFE_DH_EMISSAO,
                   NFE_CHAVE_ACESSO, NFE_PROTOCOLO, NFE_STATUS,
                   CFOP, ENTRADA_SAIDA,
                   VALOR_FINAL, TOTAL_PRODUTOS, DESCONTO,
                   VALOR_BASE_ICMS, VALOR_ICMS,
                   VALOR_BASE_ICMS_ST, VALOR_ICMS_ST,
                   VALOR_IPI, VALOR_PIS, VALOR_COFINS,
                   VALOR_FRETE, VALOR_SEGURO,
                   CANCELADO, TIPO_OPERACAO, REGIME_TRIBUTARIO
            FROM NFE
            WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ?
            ORDER BY NFE_NUMERO
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    registros.add(mapRow(rs));
                }
            }
            log.info("NFe encontradas no periodo: {}", registros.size());
        } catch (SQLException e) {
            log.error("Erro ao buscar NFe: {}", e.getMessage());
        }
        return registros;
    }

    public BigDecimal totalVendasPeriodo(Periodo periodo) {
        String sql = "SELECT COALESCE(SUM(VALOR_FINAL),0) FROM NFE WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ? AND CANCELADO = 'N'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBigDecimal(1);
        } catch (SQLException e) {
            log.error("Erro total vendas NFe: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private NfeRegistro mapRow(ResultSet rs) throws SQLException {
        return new NfeRegistro(
            rs.getLong("ID"),
            rs.getLong("ID_CLIENTE"),
            rs.getInt("NFE_NUMERO"),
            rs.getString("NFE_SERIE"),
            rs.getString("NFE_MODELO"),
            getLD(rs, "DATA_VENDA"),
            getLD(rs, "NFE_DATA_EMISSAO"),
            getLDT(rs, "NFE_DH_EMISSAO"),
            rs.getString("NFE_CHAVE_ACESSO"),
            rs.getString("NFE_PROTOCOLO"),
            rs.getString("NFE_STATUS"),
            rs.getString("CFOP"),
            rs.getString("ENTRADA_SAIDA"),
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
            rs.getString("CANCELADO"),
            rs.getString("TIPO_OPERACAO"),
            rs.getString("REGIME_TRIBUTARIO")
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
}
