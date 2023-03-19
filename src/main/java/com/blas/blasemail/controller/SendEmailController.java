package com.blas.blasemail.controller;

import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static java.time.LocalDateTime.now;

import com.blas.blascommon.core.model.AuthUser;
import com.blas.blascommon.core.model.EmailLog;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.HtmlEmailResponse;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentResponse;
import com.blas.blasemail.email.HtmlEmail;
import com.blas.blasemail.email.HtmlWithAttachmentEmail;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/send-email")
public class SendEmailController {

  @Autowired
  private HtmlEmail htmlEmail;

  @Autowired
  private HtmlWithAttachmentEmail htmlWithAttachmentEmail;

  @Autowired
  private EmailLogService emailLogService;

  @Autowired
  private AuthUserService authUserService;

  @PostMapping(value = "/html")
  public ResponseEntity<HtmlEmailResponse> sendHtmlEmailHandler(
      @RequestBody List<HtmlEmailRequest> htmlEmailPayloadList) {
    CountDownLatch latch = new CountDownLatch(htmlEmailPayloadList.size());
    List<HtmlEmailRequest> sentEmailList = new ArrayList<>();
    List<HtmlEmailRequest> failedEmailList = new ArrayList<>();
    htmlEmailPayloadList.forEach(
        email -> htmlEmail.sendEmail(email, sentEmailList, failedEmailList, latch));
    try {
      latch.await();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(), failedEmailList, sentEmailList.size(),
            sentEmailList));
    HtmlEmailResponse htmlEmailResponse = new HtmlEmailResponse();
    htmlEmailResponse.setFailedEmailNum(failedEmailList.size());
    htmlEmailResponse.setFailedEmailList(failedEmailList);
    htmlEmailResponse.setSentEmailNum(sentEmailList.size());
    htmlEmailResponse.setSentEmailList(sentEmailList);
    return ResponseEntity.ok(htmlEmailResponse);
  }

  @PostMapping(value = "/html-with-attachment")
  public ResponseEntity<HtmlEmailWithAttachmentResponse> sendHtmlWithFilesEmailHandler(
      @RequestBody List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestPayloadList) {
    CountDownLatch latch = new CountDownLatch(htmlEmailWithAttachmentRequestPayloadList.size());
    List<HtmlEmailWithAttachmentRequest> sentEmailList = new ArrayList<>();
    List<HtmlEmailWithAttachmentRequest> failedEmailList = new ArrayList<>();
    htmlEmailWithAttachmentRequestPayloadList.forEach(
        email -> htmlWithAttachmentEmail.sendEmail(email, sentEmailList, failedEmailList, latch));
    try {
      latch.await();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(), failedEmailList, sentEmailList.size(),
            sentEmailList));
    HtmlEmailWithAttachmentResponse htmlEmailWithAttachmentResponse = new HtmlEmailWithAttachmentResponse();
    htmlEmailWithAttachmentResponse.setFailedEmailNum(failedEmailList.size());
    htmlEmailWithAttachmentResponse.setFailedEmailList(failedEmailList);
    htmlEmailWithAttachmentResponse.setSentEmailNum(sentEmailList.size());
    htmlEmailWithAttachmentResponse.setSentEmailList(sentEmailList);
    return ResponseEntity.ok(htmlEmailWithAttachmentResponse);
  }

  private EmailLog buildEmailLog(int failedEmailNum, List failedEmailList, int sentEmailNum,
      List sentEmailList) {
    AuthUser generatedBy = authUserService.getAuthUserByUsername(getUsernameLoggedIn());
    return EmailLog.builder().authUser(generatedBy).timeLog(now()).failedEmailNum(failedEmailNum)
        .failedEmailList(new JSONArray(failedEmailList).toString()).sentEmailNum(sentEmailNum)
        .sentEmailList(new JSONArray(sentEmailList).toString()).build();
  }
}
