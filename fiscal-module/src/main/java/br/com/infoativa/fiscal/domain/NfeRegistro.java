package br.com.infoativa.fiscal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro completo de NF-e com JOIN ao cadastro de clientes.
 *
 * Campos adicionados (v2):
 *   nomeCliente, cnpjCliente, cpfCliente, ieCliente  → JOIN CLIENTE
 *   nfeSerie (String → String, mantido)
 *   nfeModelo (String → int, corrigido para uso no SPED)
 */
public record NfeRegistro(
    // ── Identificação ────────────────────────────────────────────────────
    long    id,
    long    idCliente,
    int     nfeNumero,
    int     nfeSerie,
    int     nfeModelo,

    // ── Datas ────────────────────────────────────────────────────────────
    LocalDate     dataVenda,
    LocalDate     nfeDataEmissao,
    LocalDateTime nfeDhEmissao,

    // ── Chaves e status ──────────────────────────────────────────────────
    String nfeChaveAcesso,
    String nfeProtocolo,
    String nfeStatus,

    // ── Operação ─────────────────────────────────────────────────────────
    String cfop,
    String entradaSaida,

    // ── Valores fiscais ──────────────────────────────────────────────────
    BigDecimal valorFinal,
    BigDecimal totalProdutos,
    BigDecimal desconto,
    BigDecimal valorBaseIcms,
    BigDecimal valorIcms,
    BigDecimal valorBaseIcmsSt,
    BigDecimal valorIcmsSt,
    BigDecimal valorIpi,
    BigDecimal valorPis,
    BigDecimal valorCofins,
    BigDecimal valorFrete,
    BigDecimal valorSeguro,

    // ── Situação ─────────────────────────────────────────────────────────
    String cancelado,
    String tipoOperacao,
    String regimeTributario,

    // ── Dados do cliente (JOIN CLIENTE) ──────────────────────────────────
    String nomeCliente,
    String cnpjCliente,
    String cpfCliente,
    String ieCliente,
    String ufCliente,
    String municipioCliente

) {
    /** true se nota foi cancelada */
    public boolean isCancelada() {
        return "S".equalsIgnoreCase(cancelado) || "1".equals(cancelado);
    }

    /** CNPJ ou CPF do destinatário (para SPED/SINTEGRA) */
    public String documentoCliente() {
        if (cnpjCliente != null && !cnpjCliente.isBlank()) return cnpjCliente;
        if (cpfCliente  != null && !cpfCliente.isBlank())  return cpfCliente;
        return "";
    }

    /** Código do participante para SPED (C + ID_CLIENTE com 6 dígitos) */
    public String codParticipante() {
        return "C" + String.format("%06d", idCliente);
    }
}
