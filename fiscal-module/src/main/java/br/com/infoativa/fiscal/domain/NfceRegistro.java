package br.com.infoativa.fiscal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record NfceRegistro(
    long id, long idCliente, int nfceNumero, String nfceSerie, String nfceModelo,
    LocalDate nfceDataEmissao, LocalDateTime nfceDhEmissao,
    String nfceChaveAcesso, String nfceProtocolo, String nfceStatus,
    String cfop, String naturezaOperacao,
    BigDecimal valorFinal, BigDecimal totalProdutos, BigDecimal totalDocumento,
    BigDecimal baseIcms, BigDecimal icms, BigDecimal icmsOutras,
    BigDecimal pis, BigDecimal cofins, BigDecimal desconto, BigDecimal acrescimo,
    String cupomCancelado, String tipoOperacao,
    String nomeCliente, String cpfCnpjCliente
) {}
