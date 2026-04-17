package br.com.infoativa.fiscal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Registro completo de item de nota de compra com dados do cabeçalho da NF.
 *
 * Campos adicionados (v2) — dados da NOTA_COMPRA (cabeçalho):
 *   nota, serie, cnpjFornecedor, nomeFornecedor, ieFornecedor,
 *   dataEmissao, nfeChave, nfeStatus, valorTotalNota
 */
public record CompraRegistro(
    // ── Item ─────────────────────────────────────────────────────────────
    long       id,
    int        item,
    long       idProduto,
    long       idNfe,
    String     codigoProduto,
    String     cfop,

    // ── Quantidades e valores do item ─────────────────────────────────────
    BigDecimal quantidade,
    BigDecimal valorUnitario,
    BigDecimal valorCompra,
    BigDecimal totalItem,

    // ── Impostos do item ──────────────────────────────────────────────────
    BigDecimal icmsValor,
    BigDecimal icmsBc,
    BigDecimal icmsTaxa,
    String     icmsCst,
    BigDecimal ipiBase,
    BigDecimal ipiTaxa,
    BigDecimal ipiValor,
    BigDecimal pisTaxa,
    BigDecimal pisValor,
    BigDecimal cofinsTaxa,
    BigDecimal cofinsValor,
    BigDecimal desconto,
    BigDecimal valorFrete,
    BigDecimal valorSeguro,

    // ── Dados do produto ─────────────────────────────────────────────────
    String descricao,
    String ncm,
    String unidade,
    String origem,

    // ── Dados do cabeçalho da Nota de Compra (JOIN NOTA_COMPRA) ──────────
    long       nota,
    int        serie,
    LocalDate  dataEmissao,
    String     nfeChave,
    String     nfeStatus,
    BigDecimal valorTotalNota,

    // ── Dados do fornecedor (JOIN FORNECEDOR / CLIENTE) ───────────────────
    String     cnpjFornecedor,
    String     nomeFornecedor,
    String     ieFornecedor,
    String     ufFornecedor

) {
    /** Código do participante para SPED */
    public String codParticipante() {
        return "F" + String.format("%06d", idNfe);
    }
}
