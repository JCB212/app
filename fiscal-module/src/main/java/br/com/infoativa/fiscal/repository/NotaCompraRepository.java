package br.com.infoativa.fiscal.repository;

import br.com.infoativa.fiscal.domain.NotaCompraRegistro;
import br.com.infoativa.fiscal.domain.Periodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class NotaCompraRepository {

    private static final Logger log = LoggerFactory.getLogger(NotaCompraRepository.class);
    private final Connection conn;

    public NotaCompraRepository(Connection conn) {
        this.conn = conn;
    }

    public List<NotaCompraRegistro> findByPeriodo(Periodo periodo) {
        List<NotaCompraRegistro> registros = new ArrayList<>();
        String sql = """
            SELECT ID, NOTA, MODELO, SERIE, ID_FORNECEDOR,
                   DATA_EMISSAO, DATA_SAIDA, NATUREZA, CFOP,
                   BASE_ICMS, VALOR_ICMS, BASE_ICMS_SUB, VALOR_ICMS_SUB,
                   VALOR_FRETE, VALOR_SEGURO, OUTRAS_DESPESAS,
                   VALOR_IPI, VALOR_PIS, VALOR_COFINS,
                   VALOR_DESCONTO, VALOR_PRODUTOS, VALOR_TOTAL,
                   NFE_CHAVE, NFE_STATUS, TIPO_OPERACAO
            FROM NOTA_COMPRA
            WHERE DATA_EMISSAO >= ? AND DATA_EMISSAO <= ?
            ORDER BY NOTA
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(periodo.inicio()));
            ps.setDate(2, Date.valueOf(periodo.fim()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    registros.add(new NotaCompraRegistro(
                        rs.getLong("ID"), safe(rs,"NOTA"), safe(rs,"MODELO"), safe(rs,"SERIE"),
                        rs.getLong("ID_FORNECEDOR"),
                        getLD(rs,"DATA_EMISSAO"), getLD(rs,"DATA_SAIDA"),
                        safe(rs,"NATUREZA"), safe(rs,"CFOP"),
                        getBD(rs,"BASE_ICMS"), getBD(rs,"VALOR_ICMS"),
                        getBD(rs,"BASE_ICMS_SUB"), getBD(rs,"VALOR_ICMS_SUB"),
                        getBD(rs,"VALOR_FRETE"), getBD(rs,"VALOR_SEGURO"), getBD(rs,"OUTRAS_DESPESAS"),
                        getBD(rs,"VALOR_IPI"), getBD(rs,"VALOR_PIS"), getBD(rs,"VALOR_COFINS"),
                        getBD(rs,"VALOR_DESCONTO"), getBD(rs,"VALOR_PRODUTOS"), getBD(rs,"VALOR_TOTAL"),
                        safe(rs,"NFE_CHAVE"), safe(rs,"NFE_STATUS"), safe(rs,"TIPO_OPERACAO")
                    ));
                }
            }
            log.info("Notas de compra no periodo: {}", registros.size());
        } catch (SQLException e) {
            log.error("Erro ao buscar notas de compra: {}", e.getMessage());
        }
        return registros;
    }

    private BigDecimal getBD(ResultSet rs, String c) throws SQLException {
        BigDecimal v = rs.getBigDecimal(c); return v != null ? v : BigDecimal.ZERO;
    }
    private LocalDate getLD(ResultSet rs, String c) throws SQLException {
        Date d = rs.getDate(c); return d != null ? d.toLocalDate() : null;
    }
    private String safe(ResultSet rs, String c) throws SQLException {
        String s = rs.getString(c); return s != null ? s.trim() : "";
    }
}
