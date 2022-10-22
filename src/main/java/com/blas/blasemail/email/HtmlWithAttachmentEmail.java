package com.blas.blasemail.email;

import static com.blas.blascommon.security.SecurityUtils.base64Decode;
import static com.blas.blascommon.utils.fileutils.FileUtils.delete;

import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentResponse;
import com.blas.blascommon.properties.EmailConfigurationProperties;
import com.blas.blascommon.utils.fileutils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Async
@Component
public class HtmlWithAttachmentEmail {

  @Autowired
  private EmailConfigurationProperties emailConfigurationProperties;

  public HtmlEmailWithAttachmentResponse sendEmail(
      List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestPayloadList) {
    Properties props = new Properties();
    props.put("mail.smtp.host", "smtp.gmail.com");
    props.put("mail.smtp.port", emailConfigurationProperties.getPortSender());
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.starttls.required", "true");
    props.put("mail.smtp.ssl.protocols", "TLSv1.2");
    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

    Session session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(emailConfigurationProperties.getEmailSender(),
            emailConfigurationProperties.getPassword());
      }
    });

    AtomicInteger sentEmailNum = new AtomicInteger();
    List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestFailedList = new ArrayList<>();
    MimeMessage message = new MimeMessage(session);
    try {
      message.setFrom(new InternetAddress(emailConfigurationProperties.getEmailSender()));
    } catch (MessagingException e) {
      e.printStackTrace();
    }
    htmlEmailWithAttachmentRequestPayloadList.forEach(htmlEmailWithAttachmentPayload -> {
      try {
        message.addRecipient(Message.RecipientType.TO,
            new InternetAddress(htmlEmailWithAttachmentPayload.getEmailTo()));
        message.setSubject(htmlEmailWithAttachmentPayload.getTitle(), "utf8");

        MimeBodyPart messageBodyPartContent = new MimeBodyPart();
        messageBodyPartContent.setContent(htmlEmailWithAttachmentPayload.getContent(),
            "text/html; charset=utf-8");
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPartContent);
        byte[] fileContent = base64Decode(htmlEmailWithAttachmentPayload.getBase64FileContent());
        FileUtils.writeByteArrayToFile(fileContent,
            "temp/" + htmlEmailWithAttachmentPayload.getFileName());
        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.attachFile(new File("temp/" + htmlEmailWithAttachmentPayload.getFileName()));
        multipart.addBodyPart(attachmentPart);
        message.setContent(multipart);
        Transport.send(message);
        sentEmailNum.getAndIncrement();
      } catch (AddressException e) {
        e.printStackTrace();
      } catch (MessagingException e) {
        e.printStackTrace();
        htmlEmailWithAttachmentRequestFailedList.add(htmlEmailWithAttachmentPayload);
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        delete("temp/" + htmlEmailWithAttachmentPayload.getFileName());
      }
    });
    HtmlEmailWithAttachmentResponse htmlEmailWithAttachmentResponse = new HtmlEmailWithAttachmentResponse();
    htmlEmailWithAttachmentResponse.setSentEmailNum(sentEmailNum.get());
    htmlEmailWithAttachmentResponse.setHtmlEmailWithAttachmentRequestFailedList(
        htmlEmailWithAttachmentRequestFailedList);
    return htmlEmailWithAttachmentResponse;
  }
}