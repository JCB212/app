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

public class NfceRepository {

    private static final Logger log = LoggerFactory.getLogger(NfceRepository.class);

    private final Connection conn;

    public NfceRepository(Connection conn) {
        this.conn = conn;
    }

    public List<NfceRegistro> findByPeriodo(Periodo periodo) {
        List<NfceRegistro> registros = new ArrayList<>();
        String sql = """
            SELECT ID, ID_CLIENTE, NFCE_NUMERO, NFCE_SERIE, NFCE_MODELO,
                   NFCE_DATA_EMISSAO, NFCE_DH_EMISSAO,
                   NFCE_CHAVE_ACESSO, NFCE_PROTOCOLO, NFCE_STATUS,
                   CFOP, NFCE_NATUREZA_OPERACAO,
                   VALOR_FINAL, TOTAL_PRODUTOS, TOTAL_DOCUMENTO,
                   BASE_ICMS, ICMS, ICMS_OUTRAS,
                   PIS, COFINS, DESCONTO, ACRESCIMO,
                   CUPOM_CANCELADO, TIPO_OPERACAO,
                   NOME_CLIENTE, CPF_CNPJ_CLIENTE
            FROM NFCE
            WHERE NFCE_DATA_EMISSAO >= ? AND NFCE_DATA_EMISSAO <= ?
            ORDER BY NFCE_NUMERO
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    registros.add(mapRow(rs));
                }
            }
            log.info("NFCe encontradas no periodo: {}", registros.size());
        } catch (SQLException e) {
            log.error("Erro ao buscar NFCe: {}", e.getMessage());
        }
        return registros;
    }

    public BigDecimal totalVendasPeriodo(Periodo periodo) {
        String sql = "SELECT COALESCE(SUM(VALOR_FINAL),0) FROM NFCE WHERE NFCE_DATA_EMISSAO >= ? AND NFCE_DATA_EMISSAO <= ? AND CUPOM_CANCELADO = 'N'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBigDecimal(1);
        } catch (SQLException e) {
            log.error("Erro total vendas NFCe: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private NfceRegistro mapRow(ResultSet rs) throws SQLException {
        return new NfceRegistro(
            rs.getLong("ID"),
            rs.getLong("ID_CLIENTE"),
            rs.getInt("NFCE_NUMERO"),
            rs.getString("NFCE_SERIE"),
            rs.getString("NFCE_MODELO"),
            getLocalDate(rs, "NFCE_DATA_EMISSAO"),
            getLocalDateTime(rs, "NFCE_DH_EMISSAO"),
            rs.getString("NFCE_CHAVE_ACESSO"),
            rs.getString("NFCE_PROTOCOLO"),
            rs.getString("NFCE_STATUS"),
            rs.getString("CFOP"),
            rs.getString("NFCE_NATUREZA_OPERACAO"),
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
            rs.getString("CUPOM_CANCELADO"),
            rs.getString("TIPO_OPERACAO"),
            rs.getString("NOME_CLIENTE"),
            rs.getString("CPF_CNPJ_CLIENTE")
        );
    }

    private BigDecimal getBD(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return v != null ? v : BigDecimal.ZERO;
    }

    private LocalDate getLocalDate(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d != null ? d.toLocalDate() : null;
    }

    private LocalDateTime getLocalDateTime(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
