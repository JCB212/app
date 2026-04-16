package br.com.infoativa.fiscal.xml;

import br.com.infoativa.fiscal.domain.XmlDocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class XmlMetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(XmlMetadataExtractor.class);
    private static final XMLInputFactory factory = XMLInputFactory.newInstance();
    private static final DateTimeFormatter FMT1 = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter FMT2 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    public static XmlDocumentInfo extract(Path xmlPath) {
        String chave = null, modelo = null, numero = null, serie = null;
        String status = null, protocolo = null;
        LocalDateTime dataEmissao = null;
        boolean cancelado = false, inutilizado = false;
        boolean inProtNFe = false, inCancNFe = false, inInutNFe = false;

        try (FileInputStream fis = new FileInputStream(xmlPath.toFile())) {
            XMLStreamReader reader = factory.createXMLStreamReader(fis, "UTF-8");
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamReader.START_ELEMENT) {
                    String name = reader.getLocalName();
                    switch (name) {
                        case "protNFe" -> inProtNFe = true;
                        case "retCancNFe", "procEventoNFe" -> inCancNFe = true;
                        case "retInutNFe" -> inInutNFe = true;
                        case "mod" -> { if (modelo == null) modelo = reader.getElementText().trim(); }
                        case "nNF" -> { if (numero == null) numero = reader.getElementText().trim(); }
                        case "serie" -> { if (serie == null) serie = reader.getElementText().trim(); }
                        case "dhEmi" -> {
                            if (dataEmissao == null) {
                                dataEmissao = parseDateTime(reader.getElementText().trim());
                            }
                        }
                        case "chNFe" -> {
                            String val = reader.getElementText().trim();
                            if (chave == null) chave = val;
                        }
                        case "nProt" -> {
                            if (inProtNFe && protocolo == null) protocolo = reader.getElementText().trim();
                        }
                        case "cStat" -> {
                            String val = reader.getElementText().trim();
                            if (inProtNFe && status == null) {
                                status = val;
                            }
                            if (inCancNFe && ("135".equals(val) || "101".equals(val))) cancelado = true;
                            if (inInutNFe && "102".equals(val)) inutilizado = true;
                        }
                    }
                } else if (event == XMLStreamReader.END_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("protNFe".equals(name)) inProtNFe = false;
                    if ("retCancNFe".equals(name) || "procEventoNFe".equals(name)) inCancNFe = false;
                    if ("retInutNFe".equals(name)) inInutNFe = false;
                }
            }
            reader.close();
        } catch (Exception e) {
            log.warn("Erro ao extrair metadados de {}: {}", xmlPath.getFileName(), e.getMessage());
            return null;
        }

        if ("100".equals(status)) status = "AUTORIZADO";

        return new XmlDocumentInfo(xmlPath, chave, dataEmissao, modelo, numero, serie,
                status, protocolo, cancelado, inutilizado);
    }

    private static LocalDateTime parseDateTime(String text) {
        try { return LocalDateTime.parse(text, FMT1); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(text, FMT2); } catch (DateTimeParseException ignored) {}
        try {
            if (text.length() > 19) text = text.substring(0, 19);
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }
}
