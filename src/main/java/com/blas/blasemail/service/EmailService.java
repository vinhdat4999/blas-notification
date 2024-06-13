package com.blas.blasemail.service;

import static com.blas.blascommon.enums.EmailTemplate.getEmailTemplate;
import static com.blas.blascommon.exceptions.BlasErrorCodeEnum.MSG_FORMATTING_ERROR;
import static com.blas.blascommon.utils.JsonUtils.maskJsonObjectWithFields;
import static com.blas.blascommon.utils.StringUtils.COMMA;
import static com.blas.blascommon.utils.StringUtils.DOT;
import static com.blas.blascommon.utils.ValidUtils.isValidEmail;
import static com.blas.blasemail.constants.EmailConstant.STATUS_SUCCESS;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.time.LocalDateTime.now;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.enums.EmailTemplate;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.utils.TemplateUtils;
import io.micrometer.core.instrument.Metrics;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Slf4j
@RequiredArgsConstructor
public abstract class EmailService<T extends EmailRequest> {

  private static final String INTERNAL_SYSTEM_MSG = "Blas Email internal error";
  private static final String INVALID_EMAIL_MSG = "Invalid receiver email: %s";
  private static final String TOO_MANY_REQUEST_TO_BLAS_EMAIL_PLEASE_TRY_AGAIN = "Too many request to blas-email. Please try again.";

  @Lazy
  protected final CentralizedLogService centralizedLogService;

  @Lazy
  protected final JavaMailSender javaMailSender;

  @Lazy
  protected final MailProperties mailProperties;

  @Lazy
  protected final TemplateUtils templateUtils;

  @Lazy
  private final Set<String> needFieldMasks;

  @Value("${blas.blas-idp.isSendEmailAlert}")
  protected boolean isSendEmailAlert;

  @Value("${blas.blas-email.numberTryToSendEmailAgain}")
  protected int numberTryToSendEmailAgain;

  @Value("${blas.blas-email.waitTimeFirstTryToSendEmailAgain}")
  protected long waitTimeFirstTryToSendEmailAgain;

  public void sendEmail(T emailRequest, List<EmailRequest> sentEmailList,
      List<EmailRequest> failedEmailList, ThreadPoolExecutor executor, CountDownLatch latch)
      throws IOException {

    String unkMessage = validateHeader(getEmailTemplate(emailRequest.getEmailTemplateName()),
        emailRequest.getData().keySet());
    MimeMessage message = javaMailSender.createMimeMessage();
    try {
      executor.execute(() -> {
        if (isInvalidReceiverEmail(emailRequest, failedEmailList, latch)) {
          return;
        }
        try {
          Thread.sleep(100);
        } catch (InterruptedException exception) {
          log.error(exception.toString());
          Thread.currentThread().interrupt();
        }
        try {
          MimeMessageHelper helper = new MimeMessageHelper(message,
              emailRequest instanceof HtmlEmailWithAttachmentRequest);
          helper.setFrom(new InternetAddress(mailProperties.getUsername()));
          helper.setTo(emailRequest.getEmailTo());
          helper.setSubject(emailRequest.getTitle());
          String htmlContent = templateUtils.generateHtmlContent(
              EmailTemplate.valueOf(emailRequest.getEmailTemplateName()),
              emailRequest.getData());

          if (emailRequest instanceof HtmlEmailWithAttachmentRequest) {
            AtomicBoolean isAddAttachFileCompletely = new AtomicBoolean(true);
            List<String> tempFileList = new ArrayList<>();
            addAttachmentToMail(message, emailRequest, isAddAttachFileCompletely, tempFileList,
                failedEmailList, emailRequest.getData(), htmlContent);
          }

          helper.setText(htmlContent, true);
          javaMailSender.send(message);
          emailRequest.setStatus(STATUS_SUCCESS);
          sentEmailList.add(emailRequest);
          Metrics.counter("blas.blas-email.number-of-first-trying").increment();
        } catch (MailException | MessagingException mailException) {
          trySendingEmail(emailRequest, message, sentEmailList, failedEmailList);
        } catch (IOException ioException) {
          errorHandler(ioException, emailRequest, failedEmailList, ioException.getMessage());
        } finally {
          emailRequest.setSentTime(now());
          emailRequest.setReasonSendFailed(
              StringUtils.isEmpty(emailRequest.getReasonSendFailed()) ? unkMessage
                  : emailRequest.getReasonSendFailed() + DOT + SPACE + unkMessage);
          latch.countDown();
        }
      });
    } catch (RejectedExecutionException exception) {
      emailRequest.setSentTime(now());
      emailRequest.setReasonSendFailed(TOO_MANY_REQUEST_TO_BLAS_EMAIL_PLEASE_TRY_AGAIN);
      saveCentralizedLog(exception, emailRequest);
      failedEmailList.add(emailRequest);
      latch.countDown();
    }
  }

  protected abstract void addAttachmentToMail(MimeMessage message, T htmlEmailWithAttachmentRequest,
      AtomicBoolean isAddAttachFileCompletely, List<String> tempFileList,
      List<EmailRequest> failedEmailList, Map<String, String> data, String htmlContent)
      throws MessagingException, FileUploadException;

  private void saveCentralizedLog(Exception exception, Object object) {
    centralizedLogService.saveLog(exception,
        maskJsonObjectWithFields(new JSONObject(javaMailSender), needFieldMasks), object,
        maskJsonObjectWithFields(new JSONObject(mailProperties), needFieldMasks));
  }

  private boolean isInvalidReceiverEmail(T emailRequest,
      List<EmailRequest> failedEmailList, CountDownLatch latch) {
    if (isValidEmail(emailRequest.getEmailTo())) {
      return false;
    }
    emailRequest.setReasonSendFailed(format(INVALID_EMAIL_MSG, emailRequest.getEmailTo()));
    failedEmailList.add(emailRequest);
    latch.countDown();
    return true;
  }

  private void trySendingEmail(T emailRequest, MimeMessage message,
      List<EmailRequest> sentEmailList, List<EmailRequest> failedEmailList) {
    int attempts = 1;
    Exception exception = null;
    while (attempts <= numberTryToSendEmailAgain) {
      try {
        long waitTime = (long) (waitTimeFirstTryToSendEmailAgain
            + waitTimeFirstTryToSendEmailAgain * (attempts - 1) * 0.5);
        TimeUnit.MILLISECONDS.sleep(waitTime);
        javaMailSender.send(message);
        sentEmailList.add(emailRequest);
        emailRequest.setStatus(STATUS_SUCCESS);
        emailRequest.setSentTime(now());
        return;
      } catch (MailException mailException) {
        exception = mailException;
        attempts++;
      } catch (InterruptedException retryException) {
        exception = retryException;
        attempts++;
        Thread.currentThread().interrupt();
      }
    }
    emailRequest.setReasonSendFailed(INTERNAL_SYSTEM_MSG);
    assert exception != null;
    saveCentralizedLog(exception, emailRequest);
    failedEmailList.add(emailRequest);
  }

  private void errorHandler(Exception exception, T emailRequest,
      List<EmailRequest> failedEmailList, String errorMessage) {
    saveCentralizedLog(exception, emailRequest);
    emailRequest.setReasonSendFailed(errorMessage);
    failedEmailList.add(emailRequest);
  }

  private String validateHeader(EmailTemplate emailTemplate, Set<String> variables)
      throws IOException {
    List<String> unknownVars = new ArrayList<>();
    Set<String> variableOfTemplate = templateUtils.getAllVariableOfThymeleafTemplate(emailTemplate);
    for (String header : variableOfTemplate) {
      if (!variables.contains(header)) {
        throw new BadRequestException(MSG_FORMATTING_ERROR,
            String.format("Header %s is required for email template: %s", header, emailTemplate));
      }
    }
    for (String variable : variables) {
      if (!variableOfTemplate.contains(variable)) {
        unknownVars.add(variable);
      }
    }
    if (isEmpty(unknownVars)) {
      return EMPTY;
    }
    return "Unknown variables: " + join(COMMA, unknownVars);
  }
}
