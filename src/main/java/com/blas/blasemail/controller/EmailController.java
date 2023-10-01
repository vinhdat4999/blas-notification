package com.blas.blasemail.controller;

import static com.blas.blascommon.enums.FileType.XLSX;
import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.security.SecurityUtils.getUserIdLoggedIn;
import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blascommon.security.SecurityUtils.isPrioritizedRole;
import static com.blas.blascommon.utils.StringUtils.DOT;
import static com.blas.blascommon.utils.fileutils.exportfile.Excel.exportToExcel;
import static java.lang.System.currentTimeMillis;
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
import com.blas.blasemail.email.HtmlEmail;
import com.blas.blasemail.email.HtmlWithAttachmentEmail;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailController {

  protected static final String INTERNAL_SYSTEM_ERROR_MSG = "Blas Email internal error. Cannot determine status of emails.";
  protected static final String EMAIL_TO = "emailTo";
  protected static final String TITLE = "title";
  protected static final String EMAIL_TEMPLATE_NAME = "emailTemplateName";
  private static final String EXCEEDED_QUOTA = "Exceeded the quota today";
  private static final String NUMBER_OF = "No";
  private static final String REASON_SEND_FAILED = "reasonSendFailed";
  private static final String STATUS_EMAIL = "statusEmail";
  private static final String SENT_TIME = "sentTime";
  @Lazy
  protected final CentralizedLogService centralizedLogService;
  @Lazy
  protected final HtmlWithAttachmentEmail htmlWithAttachmentEmail;
  @Lazy
  protected final EmailLogService emailLogService;
  @Lazy
  protected final JavaMailSender javaMailSender;
  @Lazy
  protected final ThreadPoolTaskExecutor taskExecutor;
  @Lazy
  private final HtmlEmail htmlEmail;
  @Lazy
  private final AuthUserService authUserService;
  @Value("${blas.blas-idp.isSendEmailAlert}")
  protected boolean isSendEmailAlert;
  @Value("${blas.service.serviceName}")
  private String serviceName;
  @Value("${blas.blas-email.dailyQuotaNormalUser}")
  private int dailyQuotaNormalUser;

  protected ResponseEntity<HtmlEmailResponse> sendHtmlEmail(
      List<HtmlEmailRequest> htmlEmailPayloadList, Authentication authentication,
      boolean genFileReport) throws IOException {
    int emailNum = htmlEmailPayloadList.size();
    isPrioritizedRoleOrInQuota(emailNum, authentication);
    List<EmailRequest> sentEmailList = new CopyOnWriteArrayList<>();
    List<EmailRequest> failedEmailList = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(emailNum);
    for (HtmlEmailRequest emailRequest : htmlEmailPayloadList) {
      htmlEmail.sendEmail(emailRequest, sentEmailList, failedEmailList,
          taskExecutor.getThreadPoolExecutor(), latch);
    }
    try {
      latch.await();
    } catch (InterruptedException e) {
      saveCentralizedLog(e, authentication, htmlEmailPayloadList);
      Thread.currentThread().interrupt();
      throw new BadRequestException(INTERNAL_SYSTEM_ERROR_MSG);
    }

    String fileReport = saveEmailLogFile(htmlEmailPayloadList, genFileReport);

    EmailLog emailLog = emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(), null, sentEmailList.size(), null));
    log.info(
        String.format("Sent email - email_log_id: %s - fileReport: %s", emailLog.getEmailLogId(),
            fileReport));
    return ResponseEntity.ok(HtmlEmailResponse.builder()
        .failedEmailNum(failedEmailList.size())
        .failedEmailList(failedEmailList)
        .sentEmailNum(sentEmailList.size())
        .sentEmailList(sentEmailList)
        .generatedBy(authentication.getName())
        .generatedTime(now())
        .build());
  }

  protected EmailLog buildEmailLog(int failedEmailNum, List<EmailRequest> failedEmailList,
      int sentEmailNum, List<EmailRequest> sentEmailList) {
    AuthUser generatedBy = authUserService.getAuthUserByUsername(getUsernameLoggedIn());
    return EmailLog.builder()
        .authUser(generatedBy)
        .timeLog(now())
        .failedEmailNum(failedEmailNum)
        .failedEmailList(new JSONArray(failedEmailList).toString())
        .sentEmailNum(sentEmailNum)
        .sentEmailList(new JSONArray(sentEmailList).toString())
        .build();
  }

  protected void isPrioritizedRoleOrInQuota(int emailNum, Authentication authentication) {
    Integer sentEmail = emailLogService.getNumOfSentEmailInDateOfUserId(
        getUserIdLoggedIn(authUserService), LocalDate.now());
    if (!isPrioritizedRole(authentication) && sentEmail != null
        && sentEmail + emailNum > dailyQuotaNormalUser) {
      throw new ForbiddenException(EXCEEDED_QUOTA);
    }
  }

  protected void saveCentralizedLog(InterruptedException e, Authentication authentication,
      List<? extends EmailRequest> emailRequestList) {
    centralizedLogService.saveLog(serviceName, ERROR, e.toString(),
        e.getCause() == null ? EMPTY : e.getCause().toString(),
        new JSONArray(emailRequestList).toString(), "User: " + authentication.getName(), null,
        new JSONArray(e.getStackTrace()).toString(), isSendEmailAlert);
  }

  private String saveEmailLogFile(List<HtmlEmailRequest> htmlEmailPayloadList,
      boolean genFileReport) {
    List<String> headers = new ArrayList<>();
    headers.add(NUMBER_OF);
    headers.add(EMAIL_TO);
    headers.add(TITLE);
    headers.add(EMAIL_TEMPLATE_NAME);
    headers.add(REASON_SEND_FAILED);
    headers.add(STATUS_EMAIL);
    headers.add(SENT_TIME);
    headers.addAll(htmlEmailPayloadList.get(0).getData().keySet());
    List<String[]> data = new ArrayList<>();
    for (int index = 0; index < htmlEmailPayloadList.size(); index++) {
      HtmlEmailRequest htmlEmailRequest = htmlEmailPayloadList.get(index);
      List<String> lineData = new ArrayList<>();
      lineData.add(String.valueOf(index + 1));
      lineData.add(htmlEmailRequest.getEmailTo());
      lineData.add(htmlEmailRequest.getTitle());
      lineData.add(htmlEmailRequest.getEmailTemplateName());
      lineData.add(htmlEmailRequest.getReasonSendFailed());
      lineData.add(htmlEmailRequest.getStatus());
      lineData.add(htmlEmailRequest.getSentTime().toString());
      for (int subIndex = 7; subIndex < headers.size(); subIndex++) {
        lineData.add(htmlEmailRequest.getData().get(headers.get(subIndex)));
      }
      data.add(lineData.toArray(new String[0]));
    }
    String fileReport = null;
    if (genFileReport) {
      try {
        fileReport = "temp/SEND_EMAIL_RESULT_" + currentTimeMillis() + DOT + XLSX.getPostfix();
        exportToExcel(headers.toArray(new String[0]), data, fileReport);
      } catch (IOException e) {
        log.error(e.toString());
      }
    }
    return fileReport;
  }
}
