package com.blas.blasemail.email;

import static com.blas.blascommon.enums.BlasService.BLAS_EMAIL;
import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.security.SecurityUtils.base64Decode;
import static com.blas.blascommon.utils.fileutils.FileUtils.delete;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentResponse;
import com.blas.blascommon.utils.fileutils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.mail.Authenticator;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.json.JSONArray;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Async
@Component
public class HtmlWithAttachmentEmail extends Email {

  public HtmlEmailWithAttachmentResponse sendEmail(
      List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestPayloadList) {
    final String TEMP_ELM_PATH = "temp/";
    Session session = Session.getDefaultInstance(buildEmailProperties(), new Authenticator() {
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
      centralizedLogService.saveLog(BLAS_EMAIL.getServiceName(), ERROR, e.toString(),
          e.getCause() == null ? EMPTY : e.getCause().toString(),
          new JSONArray(List.of(emailConfigurationProperties)).toString(), null, null,
          String.valueOf(new JSONArray(e.getStackTrace())), isSendEmailAlert);
      e.printStackTrace();
    }
    htmlEmailWithAttachmentRequestPayloadList.forEach(htmlEmailWithAttachmentPayload -> {
      try {
        message.addRecipient(RecipientType.TO,
            new InternetAddress(htmlEmailWithAttachmentPayload.getEmailTo()));
        message.setSubject(htmlEmailWithAttachmentPayload.getTitle(), "utf8");

        MimeBodyPart messageBodyPartContent = new MimeBodyPart();
        messageBodyPartContent.setContent(htmlEmailWithAttachmentPayload.getContent(),
            "text/html; charset=utf-8");
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPartContent);
        byte[] fileContent = base64Decode(htmlEmailWithAttachmentPayload.getBase64FileContent());
        FileUtils.writeByteArrayToFile(fileContent,
            TEMP_ELM_PATH + htmlEmailWithAttachmentPayload.getFileName());
        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.attachFile(
            new File(TEMP_ELM_PATH + htmlEmailWithAttachmentPayload.getFileName()));
        multipart.addBodyPart(attachmentPart);
        message.setContent(multipart);
        Transport.send(message);
        sentEmailNum.getAndIncrement();
      } catch (MessagingException | IOException e) {
        saveCentralizeLog(e, htmlEmailWithAttachmentPayload);
      } finally {
        delete(TEMP_ELM_PATH + htmlEmailWithAttachmentPayload.getFileName());
      }
    });
    HtmlEmailWithAttachmentResponse htmlEmailWithAttachmentResponse = new HtmlEmailWithAttachmentResponse();
    htmlEmailWithAttachmentResponse.setSentEmailNum(sentEmailNum.get());
    htmlEmailWithAttachmentResponse.setHtmlEmailWithAttachmentRequestFailedList(
        htmlEmailWithAttachmentRequestFailedList);
    return htmlEmailWithAttachmentResponse;
  }
}
