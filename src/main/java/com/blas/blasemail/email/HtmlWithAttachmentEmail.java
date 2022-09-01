package com.blas.blasemail.email;

import com.blas.blascommon.security.SecurityUtils;
import com.blas.blasemail.properties.EmailConfigurationProperties;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Async
@Component
public class HtmlWithAttachmentEmail {

  @Autowired
  private EmailConfigurationProperties emailConfigurationProperties;

  public void sendEmail(String emailTo, String title, String content, String base64FileContent)
      throws MessagingException {
    Properties props = new Properties();
    props.put("mail.smtp.host", "smtp.gmail.com");
    props.put("mail.smtp.port", emailConfigurationProperties.getPortSender());
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.starttls.required", "true");
    props.put("mail.smtp.ssl.protocols", "TLSv1.2");
    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

    Session session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(emailConfigurationProperties.getEmailSender(),
            emailConfigurationProperties.getPassword());
      }
    });

    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(emailConfigurationProperties.getEmailSender()));
    message.addRecipient(Message.RecipientType.TO, new InternetAddress(emailTo));
    message.setSubject(title, "utf8");

    MimeBodyPart messageBodyPartContent = new MimeBodyPart();
    messageBodyPartContent.setContent(content, "text/html; charset=utf-8");

    Multipart multipart = new MimeMultipart();
    multipart.addBodyPart(messageBodyPartContent);

    MimeBodyPart messageBodyPartFile = new MimeBodyPart();
    byte[] fileContent = SecurityUtils.base64Decode(base64FileContent);
    ByteArrayDataSource byteArrayDataSource = new ByteArrayDataSource(fileContent, "");
    messageBodyPartFile.setDataHandler(new DataHandler(byteArrayDataSource));
    messageBodyPartFile.setFileName(byteArrayDataSource.getName());
    multipart.addBodyPart(messageBodyPartFile);

    message.setContent(multipart);
    Transport.send(message);
  }
}