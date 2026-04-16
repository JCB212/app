package br.com.infoativa.fiscal;

import br.com.infoativa.fiscal.domain.Periodo;
import br.com.infoativa.fiscal.domain.XmlDocumentInfo;
import br.com.infoativa.fiscal.service.PeriodService;
import br.com.infoativa.fiscal.xml.XmlMetadataExtractor;
import br.com.infoativa.fiscal.zip.ZipService;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class TestRunner {

    public static void main(String[] args) throws Exception {
        int passed = 0, failed = 0;

        // Test 1: PeriodService.resolveAuto
        try {
            Periodo p = PeriodService.resolveAuto();
            YearMonth prev = YearMonth.now().minusMonths(1);
            assert p.inicio().equals(prev.atDay(1));
            assert p.fim().equals(prev.atEndOfMonth());
            System.out.println("[OK] PeriodService.resolveAuto");
            passed++;
        } catch (Exception e) {
            System.out.println("[FAIL] PeriodService.resolveAuto: " + e.getMessage());
            failed++;
        }

        // Test 2: PeriodService.resolveAnual
        try {
            List<Periodo> periodos = PeriodService.resolveAnual(2025);
            assert periodos.size() == 12;
            assert periodos.get(0).inicio().equals(LocalDate.of(2025, 1, 1));
            assert periodos.get(11).fim().equals(LocalDate.of(2025, 12, 31));
            System.out.println("[OK] PeriodService.resolveAnual");
            passed++;
        } catch (Exception e) {
            System.out.println("[FAIL] PeriodService.resolveAnual: " + e.getMessage());
            failed++;
        }

        // Test 3: PeriodService.nomeMes
        try {
            assert "Janeiro".equals(PeriodService.nomeMes(1));
            assert "Dezembro".equals(PeriodService.nomeMes(12));
            System.out.println("[OK] PeriodService.nomeMes");
            passed++;
        } catch (Exception e) {
            System.out.println("[FAIL] PeriodService.nomeMes: " + e.getMessage());
            failed++;
        }

        // Test 4: Periodo.descricao
        try {
            Periodo p = new Periodo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
            assert "03/2026 a 03/2026".equals(p.descricao());
            assert "03_2026".equals(p.mesAnoRef());
            System.out.println("[OK] Periodo.descricao");
            passed++;
        } catch (Exception e) {
            System.out.println("[FAIL] Periodo.descricao: " + e.getMessage());
            failed++;
        }

        // Test 5: XmlMetadataExtractor with sample XML
        try {
            Path sampleXml = createSampleNfeXml();
            XmlDocumentInfo info = XmlMetadataExtractor.extract(sampleXml);
            assert info != null;
            assert "55".equals(info.modelo());
            assert "AUTORIZADO".equals(info.status());
            assert info.chaveAcesso() != null;
            assert info.dataEmissao() != null;
            System.out.println("[OK] XmlMetadataExtractor.extract");
            passed++;
            Files.deleteIfExists(sampleXml);
        } catch (Exception e) {
            System.out.println("[FAIL] XmlMetadataExtractor: " + e.getMessage());
            failed++;
        }

        // Test 6: ZipService
        try {
            Path tempDir = Files.createTempDirectory("ziptest");
            Files.writeString(tempDir.resolve("test.txt"), "Teste de conteudo");
            Path subDir = Files.createDirectory(tempDir.resolve("subdir"));
            Files.writeString(subDir.resolve("test2.txt"), "Mais conteudo");
            Path zipFile = tempDir.getParent().resolve("test.zip");
            ZipService.zipDirectory(tempDir, zipFile);
            assert Files.exists(zipFile);
            assert Files.size(zipFile) > 0;
            System.out.println("[OK] ZipService.zipDirectory");
            passed++;
            // cleanup
            Files.deleteIfExists(zipFile);
        } catch (Exception e) {
            System.out.println("[FAIL] ZipService: " + e.getMessage());
            failed++;
        }

        // Test 7: XmlDocumentInfo helpers
        try {
            XmlDocumentInfo nfce = new XmlDocumentInfo(
                Path.of("test.xml"), "chave123", null, null, "65", "1", "1",
                "AUTORIZADO", "proto", false, false, false, null);
            assert nfce.isNfce();
            assert !nfce.isNfe();
            assert nfce.isAutorizado();

            XmlDocumentInfo nfe = new XmlDocumentInfo(
                Path.of("test.xml"), "chave456", null, null, "55", "2", "1",
                "100", "proto", false, false, false, null);
            assert nfe.isNfe();
            assert !nfe.isNfce();
            assert nfe.isAutorizado();
            System.out.println("[OK] XmlDocumentInfo helpers");
            passed++;
        } catch (Exception e) {
            System.out.println("[FAIL] XmlDocumentInfo: " + e.getMessage());
            failed++;
        }

        System.out.println("\n=== Resultado: " + passed + " OK, " + failed + " FALHAS ===");
        System.exit(failed > 0 ? 1 : 0);
    }

    private static Path createSampleNfeXml() throws IOException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
              <NFe>
                <infNFe versao="4.00">
                  <ide>
                    <mod>55</mod>
                    <serie>1</serie>
                    <nNF>12345</nNF>
                    <dhEmi>2026-03-15T10:30:00-03:00</dhEmi>
                  </ide>
                </infNFe>
              </NFe>
              <protNFe versao="4.00">
                <infProt>
                  <chNFe>29260159545171000300550010000123459999999990</chNFe>
                  <nProt>129260000001234</nProt>
                  <cStat>100</cStat>
                </infProt>
              </protNFe>
            </nfeProc>
            """;
        Path path = Files.createTempFile("test_nfe_", ".xml");
        Files.writeString(path, xml);
        return path;
    }
}
