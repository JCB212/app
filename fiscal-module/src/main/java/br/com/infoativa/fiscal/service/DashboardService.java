package br.com.infoativa.fiscal.service;

import br.com.infoativa.fiscal.domain.Periodo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private final Connection conn;

    public DashboardService(Connection conn) {
        this.conn = conn;
    }

    /** Vendas NFCe por mês (últimos N meses) */
    public Map<String, BigDecimal> vendasNfceMensal(int meses) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (int i = meses - 1; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            Periodo p = new Periodo(ym.atDay(1), ym.atEndOfMonth());
            String label = PeriodService.nomeMes(ym.getMonthValue()).substring(0, 3) + "/" + ym.getYear();
            BigDecimal total = querySum("SELECT COALESCE(SUM(VALOR_FINAL),0) FROM NFCE WHERE NFCE_DATA_EMISSAO >= ? AND NFCE_DATA_EMISSAO <= ? AND (CUPOM_CANCELADO = 'N' OR CUPOM_CANCELADO IS NULL)", p);
            result.put(label, total);
        }
        return result;
    }

    /** Vendas NFe por mês (últimos N meses) */
    public Map<String, BigDecimal> vendasNfeMensal(int meses) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (int i = meses - 1; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            Periodo p = new Periodo(ym.atDay(1), ym.atEndOfMonth());
            String label = PeriodService.nomeMes(ym.getMonthValue()).substring(0, 3) + "/" + ym.getYear();
            BigDecimal total = querySum("SELECT COALESCE(SUM(VALOR_FINAL),0) FROM NFE WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ? AND (CANCELADO = 'N' OR CANCELADO IS NULL) AND ENTRADA_SAIDA = '1'", p);
            result.put(label, total);
        }
        return result;
    }

    /** Compras por mês (últimos N meses) */
    public Map<String, BigDecimal> comprasMensal(int meses) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (int i = meses - 1; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            Periodo p = new Periodo(ym.atDay(1), ym.atEndOfMonth());
            String label = PeriodService.nomeMes(ym.getMonthValue()).substring(0, 3) + "/" + ym.getYear();
            BigDecimal total = querySum("SELECT COALESCE(SUM(VALOR_TOTAL),0) FROM NOTA_COMPRA WHERE DATA_EMISSAO >= ? AND DATA_EMISSAO <= ?", p);
            result.put(label, total);
        }
        return result;
    }

    /** Impostos do mês anterior (para pie chart) */
    public Map<String, BigDecimal> impostosUltimoMes() {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        YearMonth prev = YearMonth.now().minusMonths(1);
        Periodo p = new Periodo(prev.atDay(1), prev.atEndOfMonth());

        BigDecimal icmsNfce = querySum("SELECT COALESCE(SUM(ICMS),0) FROM NFCE WHERE NFCE_DATA_EMISSAO >= ? AND NFCE_DATA_EMISSAO <= ? AND (CUPOM_CANCELADO = 'N' OR CUPOM_CANCELADO IS NULL)", p);
        BigDecimal icmsNfe = querySum("SELECT COALESCE(SUM(VALOR_ICMS),0) FROM NFE WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ? AND (CANCELADO = 'N' OR CANCELADO IS NULL)", p);
        BigDecimal pisNfce = querySum("SELECT COALESCE(SUM(PIS),0) FROM NFCE WHERE NFCE_DATA_EMISSAO >= ? AND NFCE_DATA_EMISSAO <= ? AND (CUPOM_CANCELADO = 'N' OR CUPOM_CANCELADO IS NULL)", p);
        BigDecimal pisNfe = querySum("SELECT COALESCE(SUM(VALOR_PIS),0) FROM NFE WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ? AND (CANCELADO = 'N' OR CANCELADO IS NULL)", p);
        BigDecimal cofinsNfce = querySum("SELECT COALESCE(SUM(COFINS),0) FROM NFCE WHERE NFCE_DATA_EMISSAO >= ? AND NFCE_DATA_EMISSAO <= ? AND (CUPOM_CANCELADO = 'N' OR CUPOM_CANCELADO IS NULL)", p);
        BigDecimal cofinsNfe = querySum("SELECT COALESCE(SUM(VALOR_COFINS),0) FROM NFE WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ? AND (CANCELADO = 'N' OR CANCELADO IS NULL)", p);
        BigDecimal ipi = querySum("SELECT COALESCE(SUM(VALOR_IPI),0) FROM NFE WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ? AND (CANCELADO = 'N' OR CANCELADO IS NULL)", p);
        BigDecimal icmsSt = querySum("SELECT COALESCE(SUM(VALOR_ICMS_ST),0) FROM NFE WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ? AND (CANCELADO = 'N' OR CANCELADO IS NULL)", p);

        result.put("ICMS", icmsNfce.add(icmsNfe));
        result.put("PIS", pisNfce.add(pisNfe));
        result.put("COFINS", cofinsNfce.add(cofinsNfe));
        result.put("IPI", ipi);
        result.put("ICMS ST", icmsSt);
        return result;
    }

    /** Contagens rápidas para cards do dashboard */
    public int countNfceMesAnterior() {
        return queryCount("SELECT COUNT(*) FROM NFCE WHERE NFCE_DATA_EMISSAO >= ? AND NFCE_DATA_EMISSAO <= ? AND (CUPOM_CANCELADO = 'N' OR CUPOM_CANCELADO IS NULL)");
    }

    public int countNfeMesAnterior() {
        return queryCount("SELECT COUNT(*) FROM NFE WHERE NFE_DATA_EMISSAO >= ? AND NFE_DATA_EMISSAO <= ? AND (CANCELADO = 'N' OR CANCELADO IS NULL)");
    }

    public int countComprasMesAnterior() {
        return queryCount("SELECT COUNT(*) FROM NOTA_COMPRA WHERE DATA_EMISSAO >= ? AND DATA_EMISSAO <= ?");
    }

    private BigDecimal querySum(String sql, Periodo p) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(p.inicio()));
            ps.setDate(2, Date.valueOf(p.fim()));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBigDecimal(1);
        } catch (SQLException e) {
            log.warn("Erro query dashboard: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private int queryCount(String sql) {
        YearMonth prev = YearMonth.now().minusMonths(1);
        Periodo p = new Periodo(prev.atDay(1), prev.atEndOfMonth());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(p.inicio()));
            ps.setDate(2, Date.valueOf(p.fim()));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            log.warn("Erro count dashboard: {}", e.getMessage());
        }
        return 0;
    }
}
