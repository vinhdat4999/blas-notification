package com.blas.blasemail.controller;

import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.payload.EmailResponse;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blasemail.service.EmailService;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "/send-email")
public class HtmlEmailWithAttachmentController extends
    EmailController<HtmlEmailWithAttachmentRequest> {

  public HtmlEmailWithAttachmentController(CentralizedLogService centralizedLogService,
      EmailService<HtmlEmailWithAttachmentRequest> emailService, EmailLogService emailLogService,
      JavaMailSender javaMailSender, ThreadPoolTaskExecutor taskExecutor,
      AuthUserService authUserService) {
    super(centralizedLogService, emailService, emailLogService, javaMailSender, taskExecutor,
        authUserService);
  }

  @PostMapping(value = "/html-with-attachment")
  public ResponseEntity<EmailResponse> sendHtmlWithFilesEmailHandler(
      @RequestBody List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestPayloadList,
      Authentication authentication) throws IOException {
    return sendHtmlEmail(htmlEmailWithAttachmentRequestPayloadList, authentication, false);
  }
}
