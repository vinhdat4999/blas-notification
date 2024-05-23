package com.blas.blasemail.email;

import static com.blas.blascommon.security.SecurityUtils.base64Decode;
import static com.blas.blascommon.utils.IdUtils.genUUID;
import static com.blas.blascommon.utils.StringUtils.DOT;
import static com.blas.blascommon.utils.fileutils.FileUtils.writeByteArrayToFile;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.enums.EmailTemplate;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.FileAttachment;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.utils.TemplateUtils;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HtmlWithAttachmentEmail extends Email {

  private static final String ADD_ATTACHMENT_FAILED_MSG = "Failed to add file attachment: %s";
  private static final String TEMP_ELM_PATH = "temp/";

  public HtmlWithAttachmentEmail(
      CentralizedLogService centralizedLogService,
      JavaMailSender javaMailSender,
      MailProperties mailProperties,
      TemplateUtils templateUtils, Set<String> needFieldMasks) {
    super(centralizedLogService, javaMailSender, mailProperties, templateUtils, needFieldMasks);
  }

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
    AtomicBoolean isAddAttachFileCompletely = new AtomicBoolean(true);
    new Thread(() -> {
      if (isInvalidReceiverEmail(htmlEmailWithAttachmentRequest, failedEmailList, latch)) {
        return;
      }
      if (isBlank(htmlEmailWithAttachmentRequest.getEmailTemplateName())) {
        saveCentralizeLog(new NullPointerException(INVALID_EMAIL_TEMPLATE),
            htmlEmailWithAttachmentRequest);
        htmlEmailWithAttachmentRequest.setReasonSendFailed(INVALID_EMAIL_TEMPLATE);
        failedEmailList.add(htmlEmailWithAttachmentRequest);
      }
      List<String> tempFileList = new ArrayList<>();
      MimeMessage message = javaMailSender.createMimeMessage();
      try {
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(new InternetAddress(mailProperties.getUsername()));
        helper.setTo(htmlEmailWithAttachmentRequest.getEmailTo());
        helper.setSubject(htmlEmailWithAttachmentRequest.getTitle());
        MimeBodyPart messageBodyPartContent = new MimeBodyPart();
        messageBodyPartContent.setContent(templateUtils.generateHtmlContent(
            EmailTemplate.valueOf(htmlEmailWithAttachmentRequest.getEmailTemplateName()),
            htmlEmailWithAttachmentRequest.getData()), "text/html; charset=utf-8");
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPartContent);
        addAttachments(multipart, htmlEmailWithAttachmentRequest, tempFileList,
            isAddAttachFileCompletely);
        if (!isAddAttachFileCompletely.get()) {
          throw new FileUploadException();
        }
        message.setContent(multipart);
        javaMailSender.send(message);
        sentEmailList.add(htmlEmailWithAttachmentRequest);
      } catch (FileUploadException exception) {
        saveCentralizeLog(exception, htmlEmailWithAttachmentRequest);
        failedEmailList.add(htmlEmailWithAttachmentRequest);
      } catch (MailException | MessagingException mailException) {
        trySendingEmail(htmlEmailWithAttachmentRequest, message, sentEmailList, failedEmailList);
      } catch (IOException ioException) {
        errorHandler(ioException, htmlEmailWithAttachmentRequest, failedEmailList,
            INTERNAL_SYSTEM_MSG);
      } catch (IllegalArgumentException illArgException) {
        errorHandler(illArgException, htmlEmailWithAttachmentRequest, failedEmailList,
            INVALID_EMAIL_TEMPLATE);
      } finally {
        unknownVariableHandler(htmlEmailWithAttachmentRequest, tempFileList, latch);
      }
    }).start();
  }

  private void addAttachments(Multipart multipart,
      HtmlEmailWithAttachmentRequest htmlEmailWithAttachmentRequest, List<String> tempFileList,
      AtomicBoolean isAddAttachFileCompletely) {
    for (FileAttachment fileAttach : htmlEmailWithAttachmentRequest.getFileList()) {
      String tempFileName = genUUID();
      String fileName = fileAttach.getFileName();
      try {
        byte[] fileContent = base64Decode(fileAttach.getFileContent());
        writeByteArrayToFile(fileContent, TEMP_ELM_PATH + tempFileName);
        tempFileList.add(tempFileName);
        addAttachment(multipart, fileName, TEMP_ELM_PATH + tempFileName);
      } catch (IOException e) {
        addAttachmentFailedHandler(htmlEmailWithAttachmentRequest, fileName,
            isAddAttachFileCompletely);
      } catch (MessagingException e1) {
        // Second try to add attachment file
        try {
          addAttachment(multipart, fileName, TEMP_ELM_PATH + tempFileName);
        } catch (MessagingException e2) {
          addAttachmentFailedHandler(htmlEmailWithAttachmentRequest, fileName,
              isAddAttachFileCompletely);
        }
      }
      if (!isAddAttachFileCompletely.get()) {
        break;
      }
    }
  }

  private void addAttachmentFailedHandler(
      HtmlEmailWithAttachmentRequest htmlEmailWithAttachmentRequest, String fileName,
      AtomicBoolean isAddAttachFileCompletely) {
    isAddAttachFileCompletely.set(false);
    htmlEmailWithAttachmentRequest.setReasonSendFailed(format(ADD_ATTACHMENT_FAILED_MSG, fileName));
  }

  private void deleteTempFileList(List<String> tempFileList) {
    tempFileList.forEach(tempFile -> {
      try {
        Files.delete(Path.of(TEMP_ELM_PATH + tempFile));
      } catch (IOException e) {
        saveCentralizeLog(e, EMPTY);
      }
    });
  }

  private void unknownVariableHandler(HtmlEmailWithAttachmentRequest htmlEmailWithAttachmentRequest,
      List<String> tempFileList, CountDownLatch latch) {
    try {
      String unkMessage = validateHeader(
          EmailTemplate.valueOf(htmlEmailWithAttachmentRequest.getEmailTemplateName()),
          htmlEmailWithAttachmentRequest.getData().keySet());
      htmlEmailWithAttachmentRequest.setReasonSendFailed(
          isEmpty(htmlEmailWithAttachmentRequest.getReasonSendFailed()) ? unkMessage
              : DOT + SPACE + unkMessage);
    } catch (IOException e) {
      log.error(e.toString());
    }
    deleteTempFileList(tempFileList);
    latch.countDown();
  }
}
