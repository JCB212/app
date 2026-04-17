package br.com.infoativa.fiscal.domain;

import java.nio.file.Path;

/**
 * Resultado do processamento de fechamento fiscal.
 */
public record ProcessamentoResult(
    Periodo periodo,
    Path outputDir,
    Path zipFile,
    int totalXmls,
    int xmlsNfe,
    int xmlsNfce,
    int xmlsCompras,
    boolean sucesso,
    String mensagem
) {
    public static ProcessamentoResult sucesso(Periodo periodo, Path outputDir, Path zipFile,
                                               int total, int nfes, int nfces, int compras) {
        return new ProcessamentoResult(periodo, outputDir, zipFile,
                total, nfes, nfces, compras, true,
                "Processamento concluído com sucesso. " + total + " XMLs processados.");
    }

    public static ProcessamentoResult semXmls(Periodo periodo, String msg) {
        return new ProcessamentoResult(periodo, null, null,
                0, 0, 0, 0, false, msg);
    }

    public static ProcessamentoResult erro(String mensagem) {
        return new ProcessamentoResult(null, null, null,
                0, 0, 0, 0, false, mensagem);
    }

    public String resumo() {
        if (!sucesso) return "⚠️ " + mensagem;
        return String.format(
            "✅ %d XMLs processados (NFe: %d | NFCe: %d | Compras: %d)%nPeríodo: %s",
            totalXmls, xmlsNfe, xmlsNfce, xmlsCompras,
            periodo != null ? periodo.descricao() : "N/A"
        );
    }
}
