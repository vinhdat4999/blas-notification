package com.blas.blasemail.controller;

import static java.time.LocalDateTime.now;

import com.blas.blascommon.core.model.EmailLog;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentResponse;
import com.blas.blasemail.email.HtmlEmail;
import com.blas.blasemail.email.HtmlWithAttachmentEmail;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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
public class HtmlEmailWithAttachmentController extends EmailController {

  public HtmlEmailWithAttachmentController(CentralizedLogService centralizedLogService,
      HtmlWithAttachmentEmail htmlWithAttachmentEmail, EmailLogService emailLogService,
      JavaMailSender javaMailSender, ThreadPoolTaskExecutor taskExecutor, HtmlEmail htmlEmail,
      AuthUserService authUserService) {
    super(centralizedLogService, htmlWithAttachmentEmail, emailLogService, javaMailSender,
        taskExecutor, htmlEmail, authUserService);
  }

  @PostMapping(value = "/html-with-attachment")
  public ResponseEntity<HtmlEmailWithAttachmentResponse> sendHtmlWithFilesEmailHandler(
      @RequestBody List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestPayloadList,
      Authentication authentication) {
    int emailNum = htmlEmailWithAttachmentRequestPayloadList.size();
    isPrioritizedRoleOrInQuota(emailNum, authentication);
    List<EmailRequest> sentEmailList = new CopyOnWriteArrayList<>();
    List<EmailRequest> failedEmailList = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(emailNum);
    htmlEmailWithAttachmentRequestPayloadList.forEach(
        email -> htmlWithAttachmentEmail.sendEmail(email, sentEmailList, failedEmailList, latch));
    try {
      latch.await();
    } catch (InterruptedException e) {
      saveCentralizedLog(e, authentication, htmlEmailWithAttachmentRequestPayloadList);
      Thread.currentThread().interrupt();
      throw new BadRequestException(INTERNAL_SYSTEM_ERROR_MSG);
    }
    EmailLog emailLog = emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(), failedEmailList, sentEmailList.size(),
            sentEmailList));
    log.info(
        String.format("Sent email - email_log_id: %s - fileReport: null",
            emailLog.getEmailLogId()));
    return ResponseEntity.ok(
        HtmlEmailWithAttachmentResponse.builder().failedEmailNum(failedEmailList.size())
            .failedEmailList(failedEmailList).sentEmailNum(sentEmailList.size())
            .sentEmailList(sentEmailList).generatedBy(authentication.getName()).generatedTime(now())
            .build());
  }
}
