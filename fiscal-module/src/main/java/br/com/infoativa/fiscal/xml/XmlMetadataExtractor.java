package br.com.infoativa.fiscal.xml;

import br.com.infoativa.fiscal.domain.XmlDocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeParseException;

/**
 * Extrator de metadados de XML NF-e / NFC-e via StAX (stream, alto desempenho).
 * Extrai: chave, dhEmi, status, protocolo, modelo, número, série.
 * Usa pré-filtro por lastModified para máxima performance.
 */
public final class XmlMetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(XmlMetadataExtractor.class);
    private static final XMLInputFactory FACTORY;

    static {
        FACTORY = XMLInputFactory.newInstance();
        FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        FACTORY.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
    }

    private XmlMetadataExtractor() {}

    /**
     * Extrai metadados de um XML fiscal.
     * @return XmlDocumentInfo ou null em caso de erro irrecuperável
     */
    public static XmlDocumentInfo extract(Path file) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 8192)) {
            return extractFromStream(file, in);
        } catch (Exception e) {
            log.debug("Erro ao extrair metadados de {}: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }

    private static XmlDocumentInfo extractFromStream(Path file, InputStream in) throws XMLStreamException {
        XMLStreamReader reader = FACTORY.createXMLStreamReader(in);
        try {
            String chave = null, dhEmi = null, status = null, protocolo = null;
            String modelo = null, numero = null, serie = null;
            boolean autorizado = false, cancelado = false, inutilizado = false;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event != XMLStreamConstants.START_ELEMENT) continue;

                String name = reader.getLocalName();
                switch (name) {
                    case "chNFe", "chNFCe", "chave" -> {
                        if (chave == null) chave = readText(reader);
                    }
                    case "dhEmi", "dEmi" -> {
                        if (dhEmi == null) dhEmi = readText(reader);
                    }
                    case "cStat" -> {
                        if (status == null) status = readText(reader);
                    }
                    case "nProt" -> {
                        if (protocolo == null) protocolo = readText(reader);
                    }
                    case "mod" -> {
                        if (modelo == null) modelo = readText(reader);
                    }
                    case "nNF", "nNFe" -> {
                        if (numero == null) numero = readText(reader);
                    }
                    case "serie" -> {
                        if (serie == null) serie = readText(reader);
                    }
                    case "protNFe", "protNFCe" -> autorizado = true;
                    case "cancNFe", "cancNFCe", "procEventoNFe" -> cancelado = true;
                    case "inutNFe" -> inutilizado = true;
                }

                // Otimização: parar quando tivermos todos os dados essenciais
                if (chave != null && dhEmi != null && status != null && modelo != null) {
                    break;
                }
            }

            LocalDateTime dataEmissao = parseDataEmissao(dhEmi);

            // Mapear cStat para status legível
            String statusLegivel = mapStatus(status);

            return new XmlDocumentInfo(
                file, chave, dataEmissao,
                modelo, numero, serie,
                statusLegivel, protocolo,
                cancelado, inutilizado
            );
        } finally {
            try { reader.close(); } catch (Exception ignored) {}
        }
    }

    private static LocalDateTime parseDataEmissao(String dhEmi) {
        if (dhEmi == null || dhEmi.isBlank()) return null;
        try {
            // Formatos comuns: "2024-01-15T10:30:00-03:00" ou "2024-01-15T10:30:00"
            String normalized = dhEmi.trim();

            // Remover offset de fuso horário
            if (normalized.length() > 19) {
                // Tentar com ZonedDateTime
                try {
                    return ZonedDateTime.parse(normalized)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDateTime();
                } catch (DateTimeParseException ignored) {}
                // Truncar offset
                normalized = normalized.substring(0, 19);
            }
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            log.debug("Erro ao parsear data de emissão '{}': {}", dhEmi, e.getMessage());
            return null;
        }
    }

    private static String mapStatus(String cStat) {
        if (cStat == null) return "DESCONHECIDO";
        return switch (cStat.trim()) {
            case "100"  -> "AUTORIZADO";
            case "101", "135" -> "CANCELADO";
            case "110"  -> "DENEGADO";
            case "150", "151" -> "CONTINGENCIA";
            case "205", "206" -> "INUTILIZADO";
            default -> cStat.trim().isEmpty() ? "DESCONHECIDO" : cStat.trim();
        };
    }

    private static String readText(XMLStreamReader reader) {
        try {
            String text = reader.getElementText();
            return text != null ? text.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
