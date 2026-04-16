package br.com.infoativa.fiscal.domain;

import java.math.BigDecimal;

/**
 * Itens da NFe (tabela NFE_ITENS no Firebird).
 * Estrutura espelha NFCE_ITENS mas vinculado a tabela NFE.
 */
public record NfeItemRegistro(
    long id, long idProduto, long idNfe,
    String cfop, String gtin, int item,
    BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal, BigDecimal totalItem,
    BigDecimal baseIcms, BigDecimal taxaIcms, BigDecimal icms,
    BigDecimal taxaPis, BigDecimal pis, BigDecimal taxaCofins, BigDecimal cofins,
    BigDecimal desconto, BigDecimal acrescimo,
    String cst, String csosn, String cancelado,
    BigDecimal icmsSt, BigDecimal icmsBcSt, BigDecimal icmsTaxaSt, BigDecimal icmsValorSt,
    BigDecimal ipiBase, BigDecimal ipiTaxa, BigDecimal ipiValor,
    BigDecimal redBaseIcms, String origem, String descricao, String ncm, String unidade
) {}
