package com.blas.blasemail.email;

import static com.blas.blascommon.enums.BlasService.BLAS_EMAIL;
import static com.blas.blascommon.enums.LogType.ERROR;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.HtmlEmailResponse;
import com.blas.blascommon.properties.EmailConfigurationProperties;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Async
public class HtmlEmail {

  @Autowired
  private CentralizedLogService centralizedLogService;

  @Autowired
  private EmailConfigurationProperties emailConfigurationProperties;

  public HtmlEmailResponse sendEmail(List<HtmlEmailRequest> htmlEmailPayloadList) {
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
    MimeMessage message = new MimeMessage(session);
    try {
      message.setFrom(new InternetAddress(emailConfigurationProperties.getEmailSender()));
    } catch (MessagingException e) {
      centralizedLogService.saveLog(BLAS_EMAIL.getServiceName(), ERROR, e.toString(),
          e.getCause() == null ? EMPTY : e.getCause().toString(),
          new JSONArray(List.of(emailConfigurationProperties)).toString(), null, null,
          String.valueOf(new JSONArray(e.getStackTrace())));
      e.printStackTrace();
    }
    MimeBodyPart messageBodyPartContent = new MimeBodyPart();
    AtomicInteger sentEmailNum = new AtomicInteger();
    List<HtmlEmailRequest> htmlEmailRequestFailedList = new ArrayList<>();
    htmlEmailPayloadList.forEach(htmlEmailPayload -> {
      try {
        message.addRecipient(Message.RecipientType.TO,
            new InternetAddress(htmlEmailPayload.getEmailTo()));
        message.setSubject(htmlEmailPayload.getTitle(), "utf8");
        messageBodyPartContent.setContent(htmlEmailPayload.getContent(),
            "text/html; charset=utf-8");
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPartContent);
        message.setContent(multipart);
        Transport.send(message);
        sentEmailNum.getAndIncrement();
      } catch (AddressException e) {
        centralizedLogService.saveLog(BLAS_EMAIL.getServiceName(), ERROR, e.toString(),
            e.getCause() == null ? EMPTY : e.getCause().toString(),
            new JSONArray(List.of(emailConfigurationProperties)).toString(),
            new JSONObject(htmlEmailPayload).toString(), null,
            String.valueOf(new JSONArray(e.getStackTrace())));
        e.printStackTrace();
      } catch (MessagingException e) {
        centralizedLogService.saveLog(BLAS_EMAIL.getServiceName(), ERROR, e.toString(),
            e.getCause() == null ? EMPTY : e.getCause().toString(),
            new JSONArray(List.of(emailConfigurationProperties)).toString(),
            new JSONObject(htmlEmailPayload).toString(), null,
            String.valueOf(new JSONArray(e.getStackTrace())));
        e.printStackTrace();
        htmlEmailRequestFailedList.add(htmlEmailPayload);
      }
    });
    HtmlEmailResponse htmlEmailResponse = new HtmlEmailResponse();
    htmlEmailResponse.setSentEmailNum(sentEmailNum.get());
    htmlEmailResponse.setHtmlEmailRequestFailedList(htmlEmailRequestFailedList);
    return htmlEmailResponse;
  }
}
