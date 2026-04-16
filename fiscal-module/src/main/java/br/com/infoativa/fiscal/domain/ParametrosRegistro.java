package br.com.infoativa.fiscal.domain;

public record ParametrosRegistro(
    long id,
    String aliquotaIcms,
    String naturezaOperacaoNfe,
    String nfeCertificadoNumserie,
    String nfeWebserviceUf,
    String nfeWebserviceAmbiente,
    String nfeDanfeLogomarca,
    String nfeSerie,
    String nfeEmailSmtphost,
    String nfeEmailSmtpport,
    String nfeEmailSmtpuser,
    String nfeEmailSmtppass,
    String nfeEmailAssunto,
    String nfeEmailMsg,
    String nfeEmailEnvio,
    String nfeEmailSsl,
    String nfeEmailTls,
    String modoSenha,
    String versaoBanco,
    String hashS,
    String envioXml,
    String atividadeIbscbs,
    String regimeTributario
) {}
