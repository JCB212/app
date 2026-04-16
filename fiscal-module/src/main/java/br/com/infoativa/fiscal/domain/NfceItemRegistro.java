package br.com.infoativa.fiscal.domain;

import java.math.BigDecimal;

public record NfceItemRegistro(
    long id, long idProduto, long idNfce,
    String cfop, String gtin, int item,
    BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal, BigDecimal totalItem,
    BigDecimal baseIcms, BigDecimal taxaIcms, BigDecimal icms,
    BigDecimal taxaPis, BigDecimal pis, BigDecimal taxaCofins, BigDecimal cofins,
    BigDecimal desconto, BigDecimal acrescimo,
    String cst, String csosn, String cancelado,
    BigDecimal icmsMonoRetValor, BigDecimal icmsMonoRetTaxa, BigDecimal icmsMonoRetQtde,
    BigDecimal redBaseIcms
) {}
