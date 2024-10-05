package com.blas.blasemail.service;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.utils.TemplateUtils;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HtmlEmailService extends EmailService<HtmlEmailRequest> {

  public HtmlEmailService(CentralizedLogService centralizedLogService,
      JavaMailSender javaMailSender, MailProperties mailProperties, TemplateUtils templateUtils,
      Set<String> needFieldMasks) {
    super(centralizedLogService, javaMailSender, mailProperties, templateUtils, needFieldMasks);
  }

  @Override
  protected void addAttachmentToMail(MimeMessage message,
      HtmlEmailRequest htmlEmailWithAttachmentRequest, AtomicBoolean isAddAttachFileCompletely,
      List<String> tempFileList, List<EmailRequest> failedEmailList, Map<String, String> data,
      String htmlContent) {
    // No action
  }
}
