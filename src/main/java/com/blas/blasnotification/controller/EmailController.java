package com.blas.blasnotification.controller;

import static com.blas.blascommon.constants.MdcConstants.EMAIL_LOG_ID;
import static com.blas.blascommon.constants.MdcConstants.GLOBAL_ID;
import static com.blas.blascommon.enums.FileType.XLSX;
import static com.blas.blascommon.security.SecurityUtils.getUserIdLoggedIn;
import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blascommon.security.SecurityUtils.isPrioritizedRole;
import static com.blas.blascommon.utils.JsonUtils.maskJsonWithFields;
import static com.blas.blascommon.utils.StringUtils.DOT;
import static com.blas.blascommon.utils.fileutils.exportfile.Excel.exportToExcel;
import static com.blas.blasnotification.utils.EmailUtils.buildSendingResult;
import static java.lang.System.currentTimeMillis;
import static java.time.LocalDateTime.now;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.blas.blascommon.core.model.AuthUser;
import com.blas.blascommon.core.model.EmailLog;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.exceptions.types.ForbiddenException;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.EmailResponse;
import com.blas.blasnotification.service.EmailService;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Slf4j
@RequiredArgsConstructor
public class EmailController<T extends EmailRequest> {

  protected static final String EMAIL_TO = "emailTo";
  protected static final String TITLE = "title";
  protected static final String EMAIL_TEMPLATE_NAME = "emailTemplateName";
  private static final String EXCEEDED_QUOTA = "Exceeded the quota today";
  private static final String NUMBER_OF = "No";
  private static final String REASON_SEND_FAILED = "reasonSendFailed";
  private static final String STATUS_EMAIL = "statusEmail";
  private static final String SENT_TIME = "sentTime";
  private static final String SYSTEM = "system";

  @Lazy
  protected final CentralizedLogService centralizedLogService;

  @Lazy
  protected final EmailService<T> emailService;

  @Lazy
  protected final EmailLogService emailLogService;

  @Lazy
  private final AuthUserService authUserService;

  @Value("${blas.blas-notification.dailyQuotaNormalUser}")
  private int dailyQuotaNormalUser;

  @Lazy
  @Resource(name = "needFieldMasks")
  protected final Set<String> needFieldMasks;

  protected ResponseEntity<EmailResponse> sendHtmlEmail(
      List<T> emailPayloads, Authentication authentication, boolean genFileReport)
      throws IOException {
    int emailNum = emailPayloads.size();
    isPrioritizedRoleOrInQuota(emailNum, authentication);

    List<CompletableFuture<EmailRequest>> sendEmailTaskFutures = new ArrayList<>();
    for (T emailRequest : emailPayloads) {
      CompletableFuture<EmailRequest> sendEmailTask = emailService.sendEmail(emailRequest);
      sendEmailTaskFutures.add(sendEmailTask);
    }
    List<EmailRequest> sentEmailList = new ArrayList<>();
    List<EmailRequest> failedEmailList = new ArrayList<>();

    buildSendingResult(sendEmailTaskFutures, sentEmailList, failedEmailList);

    String fileReport = saveEmailLogFile(emailPayloads, genFileReport);

    EmailLog emailLog = emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(),
            maskJsonWithFields(new JSONArray(failedEmailList), needFieldMasks),
            sentEmailList.size(),
            maskJsonWithFields(new JSONArray(sentEmailList), needFieldMasks)), false);

    log.info("Email sending processed - email_log_id: {} - fileReport: {}",
        emailLog.getEmailLogId(), fileReport);
    return ResponseEntity.ok(EmailResponse.builder()
        .failedEmailNum(failedEmailList.size())
        .failedEmailList(failedEmailList)
        .sentEmailNum(sentEmailList.size())
        .sentEmailList(sentEmailList)
        .generatedBy(authentication.getName())
        .generatedTime(now())
        .build());
  }

  protected EmailLog buildEmailLog(int failedEmailNum, JSONArray failedEmailList, int sentEmailNum,
      JSONArray sentEmailList) {
    String username;
    try {
      username = getUsernameLoggedIn();
    } catch (Exception exception) {
      username = SYSTEM;
    }
    AuthUser generatedBy = authUserService.getAuthUserByUsername(username);

    String initEmailLogId = MDC.get(EMAIL_LOG_ID);
    if (isBlank(initEmailLogId)) {
      initEmailLogId = UUID.randomUUID().toString();
    }
    return EmailLog.builder()
        .emailLogId(initEmailLogId)
        .globalId(MDC.get(GLOBAL_ID))
        .authUser(generatedBy)
        .timeLog(now())
        .failedEmailNum(failedEmailNum)
        .failedEmailList(failedEmailList.toString())
        .sentEmailNum(sentEmailNum)
        .sentEmailList(sentEmailList.toString())
        .build();
  }

  private void isPrioritizedRoleOrInQuota(int emailNum, Authentication authentication) {
    Integer sentEmail = emailLogService.getNumOfSentEmailInDateOfUserId(
        getUserIdLoggedIn(authUserService), LocalDate.now());
    if (!isPrioritizedRole(authentication) && sentEmail != null
        && sentEmail + emailNum > dailyQuotaNormalUser) {
      throw new ForbiddenException(EXCEEDED_QUOTA);
    }
  }

  private String saveEmailLogFile(List<T> htmlEmailPayloadList, boolean genFileReport) {
    String fileReport = null;
    if (genFileReport) {
      List<String> headers = new ArrayList<>();
      headers.add(NUMBER_OF);
      headers.add(EMAIL_TO);
      headers.add(TITLE);
      headers.add(EMAIL_TEMPLATE_NAME);
      headers.add(REASON_SEND_FAILED);
      headers.add(STATUS_EMAIL);
      headers.add(SENT_TIME);
      headers.addAll(htmlEmailPayloadList.getFirst().getData().keySet());
      List<String[]> data = new ArrayList<>();
      for (int index = 0; index < htmlEmailPayloadList.size(); index++) {
        T htmlEmailRequest = htmlEmailPayloadList.get(index);
        List<String> lineData = new ArrayList<>();
        lineData.add(String.valueOf(index + 1));
        lineData.add(htmlEmailRequest.getEmailTo());
        lineData.add(htmlEmailRequest.getTitle());
        lineData.add(htmlEmailRequest.getEmailTemplateName());
        lineData.add(htmlEmailRequest.getReasonSendFailed());
        lineData.add(htmlEmailRequest.getStatus());
        lineData.add(String.valueOf(htmlEmailRequest.getSentTime()));
        for (int subIndex = 7; subIndex < headers.size(); subIndex++) {
          lineData.add(htmlEmailRequest.getData().get(headers.get(subIndex)));
        }
        data.add(lineData.toArray(new String[0]));
      }
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
