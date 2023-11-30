package com.blas.blasemail.controller;

import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.HtmlEmailResponse;
import com.blas.blasemail.email.HtmlEmail;
import com.blas.blasemail.email.HtmlWithAttachmentEmail;
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
public class HtmlEmailController extends EmailController {

  public HtmlEmailController(CentralizedLogService centralizedLogService, HtmlEmail htmlEmail,
      HtmlWithAttachmentEmail htmlWithAttachmentEmail, EmailLogService emailLogService,
      JavaMailSender javaMailSender, ThreadPoolTaskExecutor taskExecutor,
      AuthUserService authUserService) {
    super(centralizedLogService, htmlEmail, htmlWithAttachmentEmail, emailLogService,
        javaMailSender,
        taskExecutor, authUserService);
  }

  @PostMapping(value = "/html")
  public ResponseEntity<HtmlEmailResponse> sendHtmlEmailHandler(
      @RequestBody List<HtmlEmailRequest> htmlEmailPayloadList, Authentication authentication)
      throws IOException {
    return sendHtmlEmail(htmlEmailPayloadList, authentication, false);
  }
}
