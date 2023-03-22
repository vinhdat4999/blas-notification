package com.blas.blasemail.email;

import static com.blas.blascommon.security.SecurityUtils.base64Decode;
import static com.blas.blascommon.utils.IdUtils.genUUID;
import static com.blas.blascommon.utils.TemplateUtils.generateHtmlContent;
import static com.blas.blascommon.utils.fileutils.FileUtils.delete;
import static com.blas.blascommon.utils.fileutils.FileUtils.writeByteArrayToFile;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Async
@Service
public class HtmlWithAttachmentEmail extends Email {

  private static final String ADD_ATTACHMENT_FAILED_MSG = "Failed to add file attachment: %s";

  private static void addAttachment(Multipart multipart, String filename, String filePath)
      throws MessagingException {
    DataSource source = new FileDataSource(filePath);
    BodyPart messageBodyPart = new MimeBodyPart();
    messageBodyPart.setDataHandler(new DataHandler(source));
    messageBodyPart.setFileName(filename);
    multipart.addBodyPart(messageBodyPart);
  }

  public void sendEmail(HtmlEmailWithAttachmentRequest htmlEmailWithAttachmentRequest,
      List<EmailRequest> sentEmailList, List<EmailRequest> failedEmailList, CountDownLatch latch) {
    final String TEMP_ELM_PATH = "temp/";
    AtomicBoolean isAddAttachFileCompletely = new AtomicBoolean(true);
    new Thread(() -> {
      if (isInvalidReceiverEmail(htmlEmailWithAttachmentRequest, failedEmailList, latch)) {
        return;
      }
      List<String> tempFileList = new ArrayList<>();
      MimeMessage message = javaMailSender.createMimeMessage();
      try {
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(new InternetAddress(mailProperties.getUsername()));
        helper.setTo(htmlEmailWithAttachmentRequest.getEmailTo());
        helper.setSubject(htmlEmailWithAttachmentRequest.getTitle());
        MimeBodyPart messageBodyPartContent = new MimeBodyPart();
        messageBodyPartContent.setContent(generateHtmlContent(templateEngine,
            htmlEmailWithAttachmentRequest.getEmailTemplateName(),
            htmlEmailWithAttachmentRequest.getData()), "text/html; charset=utf-8");
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPartContent);
        htmlEmailWithAttachmentRequest.getFileList().forEach(fileAttach -> {
          byte[] fileContent = base64Decode(fileAttach.getSecond());
          String tempFileName = genUUID();
          try {
            writeByteArrayToFile(fileContent, TEMP_ELM_PATH + tempFileName);
          } catch (IOException e) {
            saveCentralizeLog(e, EMPTY);
          }
          tempFileList.add(tempFileName);
          try {
            addAttachment(multipart, fileAttach.getFirst(), TEMP_ELM_PATH + tempFileName);
          } catch (MessagingException e1) {

            // Second try to add attachment file
            try {
              addAttachment(multipart, fileAttach.getFirst(), TEMP_ELM_PATH + tempFileName);
            } catch (MessagingException e2) {
              isAddAttachFileCompletely.set(false);
              htmlEmailWithAttachmentRequest.setReasonSendFailed(
                  format(ADD_ATTACHMENT_FAILED_MSG, fileAttach.getFirst()));
            }
          }
        });
        if (!isAddAttachFileCompletely.get()) {
          throw new MessagingException();
        }
        message.setContent(multipart);
        javaMailSender.send(message);
        sentEmailList.add(htmlEmailWithAttachmentRequest);
      } catch (MailException | MessagingException exception) {
        trySendingEmail(htmlEmailWithAttachmentRequest, message, sentEmailList, failedEmailList);
      } finally {
        tempFileList.forEach(tempFile -> {
          try {
            delete(TEMP_ELM_PATH + tempFile);
          } catch (IOException e) {
            saveCentralizeLog(e, EMPTY);
          }
        });
        latch.countDown();
      }
    }).start();
  }
}
