package com.blas.blasemail.controller;

import static com.blas.blascommon.enums.BlasService.BLAS_EMAIL;
import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.security.SecurityUtils.getUserIdLoggedIn;
import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blascommon.security.SecurityUtils.isPrioritizedRole;
import static java.time.LocalDateTime.now;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.model.AuthUser;
import com.blas.blascommon.core.model.EmailLog;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.exceptions.types.ForbiddenException;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.HtmlEmailResponse;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentResponse;
import com.blas.blasemail.email.HtmlEmail;
import com.blas.blasemail.email.HtmlWithAttachmentEmail;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/send-email")
public class SendEmailController {

  private static final String EXCEEDED_QUOTA = "Exceeded the quota today";
  private static final String INTERNAL_SYSTEM_ERROR_MSG = "Blas Email internal error. Cannot determine status of emails.";

  @Value("${blas.blas-idp.isSendEmailAlert}")
  protected boolean isSendEmailAlert;
  @Autowired
  protected CentralizedLogService centralizedLogService;
  List<EmailRequest> sentEmailList;
  List<EmailRequest> failedEmailList;
  @Value("${blas.blas-email.dailyQuotaNormalUser}")
  private int dailyQuotaNormalUser;
  @Autowired
  private HtmlEmail htmlEmail;
  @Autowired
  private HtmlWithAttachmentEmail htmlWithAttachmentEmail;
  @Autowired
  private EmailLogService emailLogService;
  @Autowired
  private AuthUserService authUserService;
  private CountDownLatch latch;

  @PostMapping(value = "/html")
  public ResponseEntity<HtmlEmailResponse> sendHtmlEmailHandler(
      @RequestBody List<HtmlEmailRequest> htmlEmailPayloadList, Authentication authentication) {
    setUpBeforeSendEmail(authentication, htmlEmailPayloadList);
    htmlEmailPayloadList.forEach(
        email -> htmlEmail.sendEmail(email, sentEmailList, failedEmailList, latch));
    try {
      latch.await();
    } catch (InterruptedException e) {
      saveCentralizedLog(e, authentication, htmlEmailPayloadList);
      Thread.currentThread().interrupt();
      throw new BadRequestException(INTERNAL_SYSTEM_ERROR_MSG);
    }
    emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(), failedEmailList, sentEmailList.size(),
            sentEmailList));
    return ResponseEntity.ok(HtmlEmailResponse.builder().failedEmailNum(failedEmailList.size())
        .failedEmailList(failedEmailList).sentEmailNum(sentEmailList.size())
        .sentEmailList(sentEmailList).generatedBy(authentication.getName()).generatedTime(now())
        .build());
  }

  @PostMapping(value = "/html-with-attachment")
  public ResponseEntity<HtmlEmailWithAttachmentResponse> sendHtmlWithFilesEmailHandler(
      @RequestBody List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestPayloadList,
      Authentication authentication) {
    setUpBeforeSendEmail(authentication, htmlEmailWithAttachmentRequestPayloadList);
    htmlEmailWithAttachmentRequestPayloadList.forEach(
        email -> htmlWithAttachmentEmail.sendEmail(email, sentEmailList, failedEmailList, latch));
    try {
      latch.await();
    } catch (InterruptedException e) {
      saveCentralizedLog(e, authentication, htmlEmailWithAttachmentRequestPayloadList);
      Thread.currentThread().interrupt();
      throw new BadRequestException(INTERNAL_SYSTEM_ERROR_MSG);
    }
    emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(), failedEmailList, sentEmailList.size(),
            sentEmailList));
    return ResponseEntity.ok(
        HtmlEmailWithAttachmentResponse.builder().failedEmailNum(failedEmailList.size())
            .failedEmailList(failedEmailList).sentEmailNum(sentEmailList.size())
            .sentEmailList(sentEmailList).generatedBy(authentication.getName()).generatedTime(now())
            .build());
  }

  private EmailLog buildEmailLog(int failedEmailNum, List<EmailRequest> failedEmailList,
      int sentEmailNum, List<EmailRequest> sentEmailList) {
    AuthUser generatedBy = authUserService.getAuthUserByUsername(getUsernameLoggedIn());
    return EmailLog.builder().authUser(generatedBy).timeLog(now()).failedEmailNum(failedEmailNum)
        .failedEmailList(new JSONArray(failedEmailList).toString()).sentEmailNum(sentEmailNum)
        .sentEmailList(new JSONArray(sentEmailList).toString()).build();
  }

  private void isPrioritizedRoleOrInQuota(int emailNum, Authentication authentication) {
    Integer sentEmail = emailLogService.getNumOfSentEmailInDateOfUserId(
        getUserIdLoggedIn(authUserService), LocalDate.now());
    if (!isPrioritizedRole(authentication) && sentEmail != null
        && sentEmail + emailNum > dailyQuotaNormalUser) {
      throw new ForbiddenException(EXCEEDED_QUOTA);
    }
  }

  private void saveCentralizedLog(InterruptedException e, Authentication authentication,
      List<? extends EmailRequest> emailRequestList) {
    centralizedLogService.saveLog(BLAS_EMAIL.getServiceName(), ERROR, e.toString(),
        e.getCause() == null ? EMPTY : e.getCause().toString(),
        new JSONArray(emailRequestList).toString(), "User: " + authentication.getName(), null,
        new JSONArray(e.getStackTrace()).toString(), isSendEmailAlert);
  }

  private void setUpBeforeSendEmail(Authentication authentication,
      List<? extends EmailRequest> emailRequestList) {
    isPrioritizedRoleOrInQuota(emailRequestList.size(), authentication);
    latch = new CountDownLatch(emailRequestList.size());
    sentEmailList = new ArrayList<>();
    failedEmailList = new ArrayList<>();
  }
}
