package com.blas.blasemail.controller;

import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.payload.EmailResponse;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blasemail.service.EmailService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "/send-email")
public class HtmlEmailController extends EmailController<HtmlEmailRequest> {

  public HtmlEmailController(CentralizedLogService centralizedLogService,
      EmailService<HtmlEmailRequest> emailService, EmailLogService emailLogService,
      ThreadPoolTaskExecutor taskExecutor, Set<String> needFieldMasks,
      AuthUserService authUserService) {
    super(centralizedLogService, emailService, emailLogService, taskExecutor,
        authUserService, needFieldMasks);
  }

  @PostMapping(value = "/html")
  public ResponseEntity<EmailResponse> sendHtmlEmailHandler(
      @RequestBody List<HtmlEmailRequest> htmlEmailPayloadList, Authentication authentication)
      throws IOException {
    return sendHtmlEmail(htmlEmailPayloadList, authentication, false);
  }
}
