package com.laexclusiva.service;

import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.util.Properties;

/** Proveedor SMTP con STARTTLS obligatorio, validación de certificado y credenciales efímeras. */
public final class SmtpEmailDeliveryProvider implements EmailDeliveryProvider {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;

    /** Recibe credenciales desde el entorno; nunca las imprime ni conserva en registros. */
    public SmtpEmailDeliveryProvider(String host, int port, String username, String password, String from) {
        if (host == null || host.isBlank() || port < 1 || port > 65535 || username == null || username.isBlank()
                || password == null || password.isEmpty() || from == null || from.isBlank()) {
            throw new IllegalArgumentException("La configuración SMTP está incompleta.");
        }
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
    }

    @Override public boolean isRealDelivery() { return true; }

    @Override public Result deliver(Message message) {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", Integer.toString(port));
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.ssl.checkserveridentity", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "15000");
        properties.put("mail.smtp.writetimeout", "15000");
        properties.put("mail.debug", "false");
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        session.setDebug(false);
        try {
            MimeMessage mail = new MimeMessage(session);
            mail.setFrom(new InternetAddress(from, true));
            mail.setRecipient(RecipientType.TO, new InternetAddress(message.recipient(), true));
            mail.setSubject(message.subject(), "UTF-8");
            MimeBodyPart text = new MimeBodyPart();
            text.setText(message.text(), "UTF-8");
            MimeBodyPart attachment = new MimeBodyPart();
            attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(message.attachment(), "application/pdf")));
            attachment.setFileName(message.attachmentName());
            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(text);
            multipart.addBodyPart(attachment);
            mail.setContent(multipart);
            Transport.send(mail);
            return new Result(true, "SMTP_ACCEPTED");
        } catch (Exception failure) {
            throw new IllegalStateException("El proveedor de correo no confirmó el envío.");
        }
    }
}
