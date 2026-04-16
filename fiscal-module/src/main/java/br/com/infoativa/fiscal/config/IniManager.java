package br.com.infoativa.fiscal.config;

import br.com.infoativa.fiscal.domain.EmailConfig;
import org.ini4j.Wini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IniManager {

    private static final Logger log = LoggerFactory.getLogger(IniManager.class);
    private Path xmlContadorPath;
    private Path conexaoPath;

    public AppConfig loadOrCreate() throws IOException {
        conexaoPath = findConexaoIni();
        xmlContadorPath = findOrCreateXmlContador();
        Wini conexao = new Wini(conexaoPath.toFile());
        Wini xmlCont = new Wini(xmlContadorPath.toFile());

        String ip = conexao.get("CONEXAO", "IP_SERVIDOR", String.class);
        int porta = getIntSafe(conexao, "CONEXAO", "PORTA", 3050);
        String basePath = conexao.get("CONEXAO", "BASEHOST", String.class);

        if (ip == null) ip = "127.0.0.1";
        if (basePath == null) basePath = "";

        String caminhoNfe = xmlCont.get("CONFIG", "CAMINHOXMLNFE", String.class);
        String caminhoNfce = xmlCont.get("CONFIG", "CAMINHOXMLNFCE", String.class);
        String caminhoCompras = xmlCont.get("CONFIG", "CAMINHOXMLCOMPRAS", String.class);
        boolean utilizaSenha = "SIM".equalsIgnoreCase(xmlCont.get("CONFIG", "UTILIZASENHA", String.class));
        String senhaAcesso = xmlCont.get("CONFIG", "SENHAACESSO", String.class);
        boolean utilizaSat = "SIM".equalsIgnoreCase(xmlCont.get("CONFIG", "UTILIZASAT", String.class));

        String smtpHost = xmlCont.get("CONFIG", "SMTP_HOST", String.class);
        int smtpPort = getIntSafe(xmlCont, "CONFIG", "SMTP_PORT", 465);
        String smtpUser = xmlCont.get("CONFIG", "SMTP_USUARIO", String.class);
        String smtpSenha = xmlCont.get("CONFIG", "SMTP_SENHA", String.class);
        boolean ssl = smtpPort == 465 || smtpPort == 993;

        EmailConfig emailConfig = new EmailConfig(
            smtpHost != null ? smtpHost : "mail.infoativa.com.br",
            smtpPort,
            smtpUser != null ? smtpUser : "fiscal@infoativa.com.br",
            smtpSenha != null ? smtpSenha : "",
            ssl
        );

        String destStr = xmlCont.get("CONFIG", "EMAIL_CONTADOR", String.class);
        List<String> destinatarios = new ArrayList<>();
        if (destStr != null && !destStr.isBlank()) {
            destinatarios.addAll(Arrays.asList(destStr.split(";")));
        }

        if (caminhoNfe == null) caminhoNfe = "C:\\TSD\\Host\\XML";
        if (caminhoNfce == null) caminhoNfce = "C:\\TSD\\Host\\XML_NFCe";
        if (caminhoCompras == null) caminhoCompras = "C:\\TSD\\Host\\XML_Fornecedores";

        log.info("Configuracao carregada - IP: {}, Porta: {}, Base: {}", ip, porta, basePath);
        return new AppConfig(ip, porta, basePath, caminhoNfe, caminhoNfce, caminhoCompras,
                utilizaSenha, senhaAcesso != null ? senhaAcesso : "", utilizaSat,
                emailConfig, destinatarios);
    }

    public void saveEmailConfig(EmailConfig config) throws IOException {
        Wini ini = new Wini(xmlContadorPath.toFile());
        ini.put("CONFIG", "SMTP_HOST", config.host());
        ini.put("CONFIG", "SMTP_PORT", config.port());
        ini.put("CONFIG", "SMTP_USUARIO", config.usuario());
        ini.put("CONFIG", "SMTP_SENHA", config.senha());
        ini.store();
        log.info("Configuracao de email salva");
    }

    public void saveDestinatarios(List<String> emails) throws IOException {
        Wini ini = new Wini(xmlContadorPath.toFile());
        ini.put("CONFIG", "EMAIL_CONTADOR", String.join(";", emails));
        ini.store();
        log.info("Destinatarios salvos: {}", emails.size());
    }

    public void savePaths(String nfe, String nfce, String compras) throws IOException {
        Wini ini = new Wini(xmlContadorPath.toFile());
        ini.put("CONFIG", "CAMINHOXMLNFE", nfe);
        ini.put("CONFIG", "CAMINHOXMLNFCE", nfce);
        ini.put("CONFIG", "CAMINHOXMLCOMPRAS", compras);
        ini.store();
        log.info("Caminhos XML salvos");
    }

    public void saveSenhaConfig(boolean utilizaSenha, String senha) throws IOException {
        Wini ini = new Wini(xmlContadorPath.toFile());
        ini.put("CONFIG", "UTILIZASENHA", utilizaSenha ? "SIM" : "NAO");
        ini.put("CONFIG", "SENHAACESSO", senha);
        ini.store();
    }

    private Path findConexaoIni() throws IOException {
        Path appDir = Path.of(System.getProperty("user.dir"));
        Path[] candidates = {
            appDir.resolve("conexao.ini"),
            appDir.getParent() != null ? appDir.getParent().resolve("conexao.ini") : null,
            Path.of("C:\\TSD\\Host\\conexao.ini"),
            Path.of("C:\\TSD\\conexao.ini")
        };
        for (Path p : candidates) {
            if (p != null && Files.exists(p)) {
                log.info("conexao.ini encontrado: {}", p);
                return p;
            }
        }
        Path defaultPath = appDir.resolve("conexao.ini");
        createDefaultConexao(defaultPath);
        return defaultPath;
    }

    private Path findOrCreateXmlContador() throws IOException {
        Path appDir = Path.of(System.getProperty("user.dir"));
        Path path = appDir.resolve("xmlContador.ini");
        if (!Files.exists(path)) {
            createDefaultXmlContador(path);
            log.info("xmlContador.ini criado: {}", path);
        }
        return path;
    }

    private void createDefaultConexao(Path path) throws IOException {
        String content = """
                [CONEXAO]
                IP_SERVIDOR=127.0.0.1
                PORTA=3050
                BASEHOST=C:\\TSD\\Host\\HOST.FDB
                """;
        Files.writeString(path, content);
        log.info("conexao.ini padrao criado: {}", path);
    }

    private void createDefaultXmlContador(Path path) throws IOException {
        String content = """
                [UPDATE]
                ATUALIZAR=SIM
                [CONFIG]
                UTILIZACONFIG=SIM
                UTILIZASENHA=NAO
                SENHAACESSO=123
                UTILIZASAT=NAO
                CAMINHOXMLNFE=C:\\TSD\\Host\\XML
                CAMINHOXMLNFCE=C:\\TSD\\Host\\XML_NFCe
                CAMINHOXMLCOMPRAS=C:\\TSD\\Host\\XML_Fornecedores
                EMAIL_CONTADOR=jean.carlos@infoativa.com.br
                SMTP_HOST=mail.infoativa.com.br
                SMTP_PORT=465
                SMTP_IMAP_PORT=993
                SMTP_USUARIO=fiscal@infoativa.com.br
                SMTP_SENHA=Info2024@#--
                [CONEXAO]
                IP_SERVIDOR=127.0.0.1
                PORTA=3050
                BASEHOST=C:\\TSD\\Host\\HOST.FDB
                """;
        Files.writeString(path, content);
    }

    private int getIntSafe(Wini ini, String section, String key, int defaultVal) {
        try {
            String val = ini.get(section, key, String.class);
            return val != null ? Integer.parseInt(val.trim()) : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
