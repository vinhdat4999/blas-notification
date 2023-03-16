package com.blas.blasemail.email;

import static com.blas.blascommon.security.SecurityUtils.base64Decode;
import static com.blas.blascommon.utils.IdUtils.genUUID;
import static com.blas.blascommon.utils.fileutils.FileUtils.delete;
import static com.blas.blascommon.utils.fileutils.FileUtils.writeByteArrayToFile;

import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Async
@Service
public class HtmlWithAttachmentEmail extends Email {

  public HtmlEmailWithAttachmentResponse sendEmail(
      List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestPayloadList)
      throws MessagingException {
    final String TEMP_ELM_PATH = "temp/";
    MimeMessage mail = javaMailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mail, true);
    helper.setFrom(new InternetAddress(mailProperties.getUsername()));
    AtomicInteger sentEmailNum = new AtomicInteger();
    List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestFailedList = new ArrayList<>();
    List<String> tempFileList = new ArrayList<>();
    htmlEmailWithAttachmentRequestPayloadList.forEach(htmlEmailWithAttachmentPayload -> {
      try {
        helper.setTo(htmlEmailWithAttachmentPayload.getEmailTo());
        helper.setSubject(htmlEmailWithAttachmentPayload.getTitle());
        MimeBodyPart messageBodyPartContent = new MimeBodyPart();
        messageBodyPartContent.setContent(
            generateHtmlContent(htmlEmailWithAttachmentPayload.getEmailTemplateName(),
                htmlEmailWithAttachmentPayload.getData()), "text/html; charset=utf-8");
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPartContent);
        htmlEmailWithAttachmentPayload.getFileList().forEach(fileAttach -> {
          byte[] fileContent = base64Decode(fileAttach.getSecond());
          String tempFileName = genUUID();
          writeByteArrayToFile(fileContent, TEMP_ELM_PATH + tempFileName);
          tempFileList.add(tempFileName);
          try {
            addAttachment(multipart, fileAttach.getFirst(), TEMP_ELM_PATH + tempFileName);
          } catch (MessagingException e) {
            saveCentralizeLog(e, htmlEmailWithAttachmentPayload);
            htmlEmailWithAttachmentRequestFailedList.add(htmlEmailWithAttachmentPayload);
          }
        });
        mail.setContent(multipart);
        javaMailSender.send(mail);
        sentEmailNum.getAndIncrement();
      } catch (MessagingException e) {
        saveCentralizeLog(e, htmlEmailWithAttachmentPayload);
        htmlEmailWithAttachmentRequestFailedList.add(htmlEmailWithAttachmentPayload);
      } finally {
        tempFileList.forEach(tempFile -> delete(TEMP_ELM_PATH + tempFile));
      }
    });
    HtmlEmailWithAttachmentResponse htmlEmailWithAttachmentResponse = new HtmlEmailWithAttachmentResponse();
    htmlEmailWithAttachmentResponse.setSentEmailNum(sentEmailNum.get());
    htmlEmailWithAttachmentResponse.setHtmlEmailWithAttachmentRequestFailedList(
        htmlEmailWithAttachmentRequestFailedList);
    return htmlEmailWithAttachmentResponse;
  }

  private static void addAttachment(Multipart multipart, String filename, String filePath)
      throws MessagingException {
    DataSource source = new FileDataSource(filePath);
    BodyPart messageBodyPart = new MimeBodyPart();
    messageBodyPart.setDataHandler(new DataHandler(source));
    messageBodyPart.setFileName(filename);
    multipart.addBodyPart(messageBodyPart);
  }
}
