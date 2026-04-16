package br.com.infoativa.fiscal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record NfeRegistro(
    long id, long idCliente, int nfeNumero, String nfeSerie, String nfeModelo,
    LocalDate dataVenda, LocalDate nfeDataEmissao, LocalDateTime nfeDhEmissao,
    String nfeChaveAcesso, String nfeProtocolo, String nfeStatus,
    String cfop, String entradaSaida,
    BigDecimal valorFinal, BigDecimal totalProdutos, BigDecimal desconto,
    BigDecimal valorBaseIcms, BigDecimal valorIcms,
    BigDecimal valorBaseIcmsSt, BigDecimal valorIcmsSt,
    BigDecimal valorIpi, BigDecimal valorPis, BigDecimal valorCofins,
    BigDecimal valorFrete, BigDecimal valorSeguro,
    String cancelado, String tipoOperacao, String regimeTributario
) {}
