package br.com.infoativa.fiscal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NotaCompraRegistro(
    long id, String nota, String modelo, String serie,
    long idFornecedor, LocalDate dataEmissao, LocalDate dataSaida,
    String natureza, String cfop,
    BigDecimal baseIcms, BigDecimal valorIcms,
    BigDecimal baseIcmsSub, BigDecimal valorIcmsSub,
    BigDecimal valorFrete, BigDecimal valorSeguro, BigDecimal outrasDespesas,
    BigDecimal valorIpi, BigDecimal valorPis, BigDecimal valorCofins,
    BigDecimal valorDesconto, BigDecimal valorProdutos, BigDecimal valorTotal,
    String nfeChave, String nfeStatus, String tipoOperacao
) {}
