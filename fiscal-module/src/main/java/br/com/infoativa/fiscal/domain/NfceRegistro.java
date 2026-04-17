package br.com.infoativa.fiscal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro completo de NFC-e com campos de cliente separados.
 *
 * Campos adicionados (v2):
 *   cnpjCliente, cpfCliente  → separados (antes era cpfCnpjCliente unificado)
 *   ieCliente                → isenção ou IE do cliente
 *   nfceSerie (int)          → corrigido de String para int
 *   nfceModelo (int)         → corrigido para uso no SPED
 */
public record NfceRegistro(
    // ── Identificação ────────────────────────────────────────────────────
    long    id,
    long    idCliente,
    int     nfceNumero,
    int     nfceSerie,
    int     nfceModelo,

    // ── Datas ────────────────────────────────────────────────────────────
    LocalDate     nfceDataEmissao,
    LocalDateTime nfceDhEmissao,

    // ── Chaves e status ──────────────────────────────────────────────────
    String nfceChaveAcesso,
    String nfceProtocolo,
    String nfceStatus,

    // ── Operação ─────────────────────────────────────────────────────────
    String cfop,
    String naturezaOperacao,

    // ── Valores ──────────────────────────────────────────────────────────
    BigDecimal valorFinal,
    BigDecimal totalProdutos,
    BigDecimal totalDocumento,
    BigDecimal baseIcms,
    BigDecimal icms,
    BigDecimal icmsOutras,
    BigDecimal pis,
    BigDecimal cofins,
    BigDecimal desconto,
    BigDecimal acrescimo,

    // ── Situação ─────────────────────────────────────────────────────────
    String cupomCancelado,
    String tipoOperacao,

    // ── Dados do cliente ─────────────────────────────────────────────────
    String nomeCliente,
    String cnpjCliente,
    String cpfCliente,
    String ieCliente

) {
    public boolean isCancelada() {
        return "S".equalsIgnoreCase(cupomCancelado) || "1".equals(cupomCancelado);
    }

    public String documentoCliente() {
        if (cnpjCliente != null && !cnpjCliente.isBlank()) return cnpjCliente;
        if (cpfCliente  != null && !cpfCliente.isBlank())  return cpfCliente;
        return "";
    }

    public String codParticipante() {
        return "C" + String.format("%06d", idCliente);
    }
}
