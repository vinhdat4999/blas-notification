package com.blas.blasnotification.service;

import static com.blas.blascommon.security.SecurityUtils.base64Decode;
import static com.blas.blascommon.utils.fileutils.FileUtils.writeByteArrayToFile;
import static com.blas.blascommon.utils.idutils.IdUtils.genUniqueId;
import static java.lang.String.format;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.payload.FileAttachment;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.utils.TemplateUtils;
import com.blas.blasnotification.properties.MailProperties;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HtmlWithAttachmentEmailService extends EmailService<HtmlEmailWithAttachmentRequest> {

  private static final String ADD_ATTACHMENT_FAILED_MSG = "Failed to add file attachment: %s";
  private static final String TEMP_ELM_PATH = "temp/";

  public HtmlWithAttachmentEmailService(CentralizedLogService centralizedLogService,
      MailProperties mailProperties, TemplateUtils templateUtils, Set<String> needFieldMasks,
      MailSelectService mailSelectService) {
    super(centralizedLogService, mailProperties, templateUtils, needFieldMasks, mailSelectService);
  }

  @Override
  protected void addAttachmentToMail(MimeMessage message,
      HtmlEmailWithAttachmentRequest htmlEmailWithAttachmentRequest,
      AtomicBoolean isAddAttachFileCompletely, List<String> tempFileList, Map<String, String> data,
      String htmlContent) throws MessagingException, FileUploadException {
    try {
      MimeBodyPart messageBodyPartContent = new MimeBodyPart();
      messageBodyPartContent.setContent(htmlContent, "text/html; charset=utf-8");
      Multipart multipart = new MimeMultipart();
      multipart.addBodyPart(messageBodyPartContent);
      addAttachments(multipart, htmlEmailWithAttachmentRequest, tempFileList,
          isAddAttachFileCompletely);
      if (!isAddAttachFileCompletely.get()) {
        throw new FileUploadException();
      }
      message.setContent(multipart);
    } catch (FileUploadException exception) {
      throw new FileUploadException(htmlEmailWithAttachmentRequest.getReasonSendFailed());
    }
  }

  private void addAttachments(Multipart multipart,
      HtmlEmailWithAttachmentRequest htmlEmailWithAttachmentRequest, List<String> tempFileList,
      AtomicBoolean isAddAttachFileCompletely) {
    for (FileAttachment fileAttach : htmlEmailWithAttachmentRequest.getFileList()) {
      String tempFileName = genUniqueId();
      String fileName = fileAttach.getFileName();
      try {
        byte[] fileContent = base64Decode(fileAttach.getFileContent());
        writeByteArrayToFile(fileContent, TEMP_ELM_PATH + tempFileName);
        tempFileList.add(tempFileName);
        addAttachmentToMultipart(multipart, fileName, TEMP_ELM_PATH + tempFileName);
      } catch (IOException exception) {
        addAttachmentFailedHandler(htmlEmailWithAttachmentRequest, fileName,
            isAddAttachFileCompletely);
      } catch (MessagingException e1) {
        // Second try to add attachment file
        try {
          addAttachmentToMultipart(multipart, fileName, TEMP_ELM_PATH + tempFileName);
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

  private void addAttachmentToMultipart(Multipart multipart, String filename, String filePath)
      throws MessagingException {
    DataSource source = new FileDataSource(filePath);
    BodyPart messageBodyPart = new MimeBodyPart();
    messageBodyPart.setDataHandler(new DataHandler(source));
    messageBodyPart.setFileName(filename);
    multipart.addBodyPart(messageBodyPart);
  }

  private void addAttachmentFailedHandler(
      HtmlEmailWithAttachmentRequest htmlEmailWithAttachmentRequest, String fileName,
      AtomicBoolean isAddAttachFileCompletely) {
    isAddAttachFileCompletely.set(false);
    htmlEmailWithAttachmentRequest.setReasonSendFailed(format(ADD_ATTACHMENT_FAILED_MSG, fileName));
  }
}
