package br.com.infoativa.fiscal.domain;

import java.math.BigDecimal;

public record CompraRegistro(
    long id, int item, long idProduto, long idNfe, String cfop,
    BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorCompra, BigDecimal totalItem,
    BigDecimal icmsValor, BigDecimal icmsBc, BigDecimal icmsTaxa, String icmsCst,
    BigDecimal ipiBase, BigDecimal ipiTaxa, BigDecimal ipiValor,
    BigDecimal pisTaxa, BigDecimal pisValor, BigDecimal cofinsTaxa, BigDecimal cofinsValor,
    BigDecimal desconto, BigDecimal valorFrete, BigDecimal valorSeguro,
    String descricao, String ncm, String unidade, String origem
) {}
