package com.blas.blasemail.controller;

import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.security.SecurityUtils.getUserIdLoggedIn;
import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blascommon.security.SecurityUtils.isPrioritizedRole;
import static com.blas.blascommon.utils.fileutils.importfile.Excel.importFromExcel;
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
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value = "/send-email")
public class SendEmailController {

  private static final String EXCEEDED_QUOTA = "Exceeded the quota today";
  private static final String INTERNAL_SYSTEM_ERROR_MSG = "Blas Email internal error. Cannot determine status of emails.";
  private static final String EMAIL_TO = "emailTo";
  private static final String TITLE = "title";
  private static final String EMAIL_TEMPLATE_NAME = "emailTemplateName";
  private static final String COLUMN_EMAIL_TO_NOT_FOUND = "Column emailTo not found";
  private static final String COLUMN_TITLE_TO_NOT_FOUND = "Column title not found";
  private static final String COLUMN_EMAIL_TEMPLATE_NAME_TO_NOT_FOUND = "Column emailTemplateName not found";

  @Value("${blas.blas-idp.isSendEmailAlert}")
  protected boolean isSendEmailAlert;

  @Value("${blas.service.serviceName}")
  private String serviceName;

  @Lazy
  @Autowired
  protected CentralizedLogService centralizedLogService;

  List<EmailRequest> sentEmailList;

  List<EmailRequest> failedEmailList;

  @Value("${blas.blas-email.dailyQuotaNormalUser}")
  private int dailyQuotaNormalUser;

  @Lazy
  @Autowired
  private HtmlEmail htmlEmail;

  @Lazy
  @Autowired
  private HtmlWithAttachmentEmail htmlWithAttachmentEmail;

  @Lazy
  @Autowired
  private EmailLogService emailLogService;

  @Lazy
  @Autowired
  private AuthUserService authUserService;

  private CountDownLatch latch;

  @PostMapping(value = "/html")
  public ResponseEntity<HtmlEmailResponse> sendHtmlEmailHandler(
      @RequestBody List<HtmlEmailRequest> htmlEmailPayloadList, Authentication authentication) {
    return sendHtmlEmail(htmlEmailPayloadList, authentication);
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

  @PostMapping(value = "/html-by-excel")
  public ResponseEntity<HtmlEmailResponse> sendEmailByExcelFile(
      @RequestParam("email-file") MultipartFile multipartFile, Authentication authentication)
      throws IOException {

    List<String[]> data = importFromExcel(multipartFile.getInputStream(),
        multipartFile.getOriginalFilename());
    Map<String, Integer> headerMap = new HashMap<>();
    String[] headers = data.get(0);
    List<HtmlEmailRequest> htmlEmailRequests = new ArrayList<>();
    for (int index = 0; index < headers.length; index++) {
      headerMap.put(headers[index], index);
    }
    for (int index = 1; index < data.size(); index++) {
      if (!headerMap.containsKey(EMAIL_TO)) {
        throw new BadRequestException(COLUMN_EMAIL_TO_NOT_FOUND);
      }
      String[] lineData = data.get(index);
      if(lineData.length==0){
        continue;
      }
      String emailTo = lineData[headerMap.get(EMAIL_TO)];
      if (!headerMap.containsKey(TITLE)) {
        throw new BadRequestException(COLUMN_TITLE_TO_NOT_FOUND);
      }
      String title = lineData[headerMap.get(TITLE)];
      if (!headerMap.containsKey(EMAIL_TEMPLATE_NAME)) {
        throw new BadRequestException(COLUMN_EMAIL_TEMPLATE_NAME_TO_NOT_FOUND);
      }
      String emailTemplateName = lineData[headerMap.get(EMAIL_TEMPLATE_NAME)];
      Map<String, String> variables = new HashMap<>();
      for (int subIndex = 0; subIndex < headers.length; subIndex++) {
        if (subIndex != headerMap.get(EMAIL_TO) && subIndex != headerMap.get(TITLE)
            && subIndex != headerMap.get(EMAIL_TEMPLATE_NAME)) {
          variables.put(headers[subIndex], subIndex < lineData.length ? lineData[subIndex] : EMPTY);
        }
      }
      htmlEmailRequests.add(
          new HtmlEmailRequest(emailTo, title, emailTemplateName, variables, null));
    }
    return sendHtmlEmail(htmlEmailRequests, authentication);
  }

  private ResponseEntity<HtmlEmailResponse> sendHtmlEmail(
      List<HtmlEmailRequest> htmlEmailPayloadList, Authentication authentication) {
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
    centralizedLogService.saveLog(serviceName, ERROR, e.toString(),
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
