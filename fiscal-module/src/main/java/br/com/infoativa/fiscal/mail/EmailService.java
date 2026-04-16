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

public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public static boolean sendZip(EmailConfig config, List<String> destinatarios, Path zipFile, String periodoDesc) {
        if (destinatarios == null || destinatarios.isEmpty()) {
            log.warn("Nenhum destinatario configurado");
            return false;
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", config.host());
        props.put("mail.smtp.port", String.valueOf(config.port()));
        props.put("mail.smtp.auth", "true");

        if (config.ssl() || config.port() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.port", String.valueOf(config.port()));
        } else if (config.port() == 587) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "30000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.usuario(), config.senha());
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.usuario()));

            for (String dest : destinatarios) {
                if (dest != null && !dest.isBlank()) {
                    message.addRecipient(Message.RecipientType.TO, new InternetAddress(dest.trim()));
                }
            }

            String mesRef = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("MM/yyyy"));
            message.setSubject("Fechamento Fiscal - " + periodoDesc);

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("Segue em anexo o fechamento fiscal referente ao periodo: " + periodoDesc + ".\n\n"
                + "Conteudo do arquivo ZIP:\n"
                + "- XMLs de NFe, NFCe e Compras\n"
                + "- SPED Fiscal\n"
                + "- SPED Contribuicoes\n"
                + "- SINTEGRA\n"
                + "- Resumo de Vendas (PDF)\n"
                + "- Resumo de Impostos (PDF)\n"
                + "- Resumo de Compras (PDF)\n\n"
                + "Enviado automaticamente pelo Modulo Fiscal InfoAtiva.", "UTF-8");

            MimeBodyPart attachPart = new MimeBodyPart();
            attachPart.attachFile(zipFile.toFile());

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(attachPart);
            message.setContent(multipart);

            Transport.send(message);
            log.info("Email enviado com sucesso para: {}", destinatarios);
            return true;
        } catch (Exception e) {
            log.error("Erro ao enviar email: {}", e.getMessage(), e);
            return false;
        }
    }

    public static boolean testConnection(EmailConfig config) {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.host());
        props.put("mail.smtp.port", String.valueOf(config.port()));
        props.put("mail.smtp.auth", "true");
        if (config.ssl() || config.port() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");

        try {
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.usuario(), config.senha());
                }
            });
            Transport transport = session.getTransport("smtp");
            transport.connect();
            transport.close();
            log.info("Teste de conexao SMTP: OK");
            return true;
        } catch (Exception e) {
            log.error("Teste de conexao SMTP falhou: {}", e.getMessage());
            return false;
        }
    }
}
