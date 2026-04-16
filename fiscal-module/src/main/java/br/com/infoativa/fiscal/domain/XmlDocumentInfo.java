package br.com.infoativa.fiscal.domain;

import java.nio.file.Path;
import java.time.LocalDateTime;

public record XmlDocumentInfo(
    Path filePath,
    String chaveAcesso,
    LocalDateTime dataEmissao,
    LocalDateTime dataRecebimento,
    String modelo,
    String numero,
    String serie,
    String status,
    String protocolo,
    boolean cancelado,
    boolean inutilizado,
    boolean contingencia,
    String tipoContingencia
) {
    public boolean isNfce() { return "65".equals(modelo); }
    public boolean isNfe() { return "55".equals(modelo); }
    public boolean isAutorizado() { return "AUTORIZADO".equalsIgnoreCase(status) || "100".equals(status); }

    public boolean hasDateDiscrepancy() {
        if (dataEmissao == null || dataRecebimento == null) return false;
        long diffMinutes = java.time.Duration.between(dataEmissao, dataRecebimento).toMinutes();
        return diffMinutes > 60; // More than 1 hour difference = possible contingency
    }
}
