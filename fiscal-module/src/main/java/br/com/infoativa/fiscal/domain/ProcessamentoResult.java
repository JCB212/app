package br.com.infoativa.fiscal.domain;

import java.nio.file.Path;

public record ProcessamentoResult(
    int totalXmlsProcessados,
    int xmlsNfe,
    int xmlsNfce,
    int xmlsCompras,
    Path pastaOutput,
    Path arquivoZip,
    boolean emailEnviado,
    String mensagem
) {}
