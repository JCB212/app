package br.com.infoativa.fiscal.domain;

import java.nio.file.Path;
import java.time.LocalDateTime;

public record XmlDocumentInfo(
    Path filePath,
    String chaveAcesso,
    LocalDateTime dataEmissao,
    String modelo,
    String numero,
    String serie,
    String status,
    String protocolo,
    boolean cancelado,
    boolean inutilizado
) {
    public boolean isNfce() { return "65".equals(modelo); }
    public boolean isNfe() { return "55".equals(modelo); }
    public boolean isAutorizado() { return "AUTORIZADO".equalsIgnoreCase(status) || "100".equals(status); }
}
