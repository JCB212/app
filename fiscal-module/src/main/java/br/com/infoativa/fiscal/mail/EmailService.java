package br.com.infoativa.fiscal.mail;

import br.com.infoativa.fiscal.domain.EmailConfig;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;

/**
 * Serviço de e-mail SMTP com suporte duplo:
 * - Porta 465 → SSL direto (mail.smtp.ssl.enable=true)
 * - Porta 587 → STARTTLS (mail.smtp.starttls.enable=true)
 * 
 * Implementa:
 * - Teste de conexão antes do envio
 * - Log detalhado de erro SMTP (incluindo erro 535)
 * - Tratamento de AuthenticationFailedException
 * - Timeouts configurados
 */
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Envia ZIP fiscal aos destinatários configurados.
     * @return true se enviado com sucesso
     */
    public static boolean sendZip(EmailConfig config, List<String> destinatarios,
                                   Path zipFile, String periodoDesc) {
        if (destinatarios == null || destinatarios.isEmpty()) {
            log.warn("Nenhum destinatario configurado. E-mail nao enviado.");
            return false;
        }
        if (config.senha() == null || config.senha().isBlank()) {
            log.warn("Senha SMTP nao configurada. E-mail nao enviado.");
            return false;
        }

        // Testar conexão antes de enviar
        if (!testConnection(config)) {
            log.error("Teste de conexao SMTP falhou. Envio cancelado.");
            return false;
        }

        Session session = createSession(config);
        session.setDebug(true); // Para diagnóstico detalhado em log

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.usuario(), "Módulo Fiscal InfoAtiva", "UTF-8"));

            for (String dest : destinatarios) {
                if (dest != null && !dest.isBlank()) {
                    message.addRecipient(Message.RecipientType.TO,
                        new InternetAddress(dest.trim()));
                }
            }

            message.setSubject("Fechamento Fiscal - " + periodoDesc, "UTF-8");

            // Corpo do e-mail
            String corpo = buildEmailBody(periodoDesc);
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(corpo, "UTF-8", "plain");

            // Anexo ZIP
            MimeBodyPart attachPart = new MimeBodyPart();
            attachPart.attachFile(zipFile.toFile());
            attachPart.setFileName(MimeUtility.encodeText(
                zipFile.getFileName().toString(), "UTF-8", "B"));

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(attachPart);
            message.setContent(multipart);

            Transport.send(message);
            log.info("E-mail enviado com sucesso para {} destinatario(s): {}", 
                     destinatarios.size(), destinatarios);
            return true;

        } catch (AuthenticationFailedException e) {
            log.error("FALHA DE AUTENTICACAO SMTP (erro 535): usuario={}, host={}, porta={} | " +
                      "Verifique usuario/senha e se a conta permite SMTP.", 
                      config.usuario(), config.host(), config.port(), e);
            return false;
        } catch (SendFailedException e) {
            log.error("Falha ao enviar e-mail (destinatario invalido?): {}", e.getMessage(), e);
            return false;
        } catch (MessagingException e) {
            log.error("Erro SMTP ao enviar e-mail: host={}, porta={}, usuario={} | Erro: {}",
                      config.host(), config.port(), config.usuario(), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Erro inesperado ao enviar e-mail", e);
            return false;
        }
    }

    /**
     * Testa conexão SMTP sem enviar mensagem.
     * @return true se conexão OK
     */
    public static boolean testConnection(EmailConfig config) {
        Properties props = buildProperties(config);
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");

        try {
            Session session = Session.getInstance(props, createAuthenticator(config));
            try (Transport transport = session.getTransport("smtp")) {
                transport.connect(config.host(), config.port(),
                                  config.usuario(), config.senha());
                log.info("Teste de conexao SMTP: OK ({}:{})", config.host(), config.port());
                return true;
            }
        } catch (AuthenticationFailedException e) {
            log.error("Teste SMTP: AUTENTICACAO FALHOU (erro 535) para usuario '{}'. " +
                      "Verifique a senha e permissoes SMTP.", config.usuario(), e);
            return false;
        } catch (Exception e) {
            log.error("Teste SMTP: FALHA de conexao em {}:{} - {}", 
                      config.host(), config.port(), e.getMessage());
            return false;
        }
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private static Session createSession(EmailConfig config) {
        Properties props = buildProperties(config);
        return Session.getInstance(props, createAuthenticator(config));
    }

    private static Properties buildProperties(EmailConfig config) {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.host());
        props.put("mail.smtp.port", String.valueOf(config.port()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.trust", config.host());
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.writetimeout", "30000");

        // Porta 465 → SSL; Porta 587 → STARTTLS; demais → depende do flag ssl
        if (config.port() == 465 || (config.ssl() && config.port() != 587)) {
            // SSL direto
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.port", String.valueOf(config.port()));
            props.put("mail.smtp.socketFactory.fallback", "false");
            log.debug("SMTP modo: SSL (porta {})", config.port());
        } else if (config.port() == 587 || !config.ssl()) {
            // STARTTLS
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            log.debug("SMTP modo: STARTTLS (porta {})", config.port());
        }

        return props;
    }

    private static Authenticator createAuthenticator(EmailConfig config) {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.usuario(), config.senha());
            }
        };
    }

    // ── Corpo do e-mail ───────────────────────────────────────────────────────

    private static String buildEmailBody(String periodoDesc) {
        String data = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return """
            Prezado(a) Contador(a),

            Segue em anexo o fechamento fiscal automático referente ao período: %s
            Data de envio: %s

            Conteúdo do arquivo ZIP:
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            ✓ XMLs de NFe (Notas Fiscais de Saída)
            ✓ XMLs de NFCe (Cupons Fiscais)
            ✓ XMLs de Compras (Fornecedores)
            ✓ XMLs Cancelados e Inutilizados
            ✓ SPED Fiscal
            ✓ SPED Contribuições
            ✓ SINTEGRA
            ✓ Resumo de Vendas (PDF)
            ✓ Resumo de Impostos (PDF)
            ✓ Resumo de Compras (PDF)
            ✓ Relatório de Sequências
            ✓ Relatório CST/CFOP
            ✓ Relatório Monofásico
            ✓ Relatório de Devoluções

            Este e-mail foi gerado automaticamente pelo Módulo Fiscal InfoAtiva.
            
            Att,
            Módulo Fiscal InfoAtiva
            """.formatted(periodoDesc, data);
    }
}
