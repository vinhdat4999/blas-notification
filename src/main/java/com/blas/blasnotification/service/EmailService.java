package com.blas.blasnotification.service;

import static com.blas.blascommon.constants.MDCConstant.EMAIL_LOG_ID;
import static com.blas.blascommon.enums.EmailTemplate.getEmailTemplate;
import static com.blas.blascommon.exceptions.BlasErrorCodeEnum.MSG_FORMATTING_ERROR;
import static com.blas.blascommon.utils.JsonUtils.maskJsonWithFields;
import static com.blas.blascommon.utils.StringUtils.COMMA;
import static com.blas.blascommon.utils.StringUtils.DOT;
import static com.blas.blascommon.utils.ValidUtils.isValidEmail;
import static com.blas.blasnotification.constants.EmailConstant.STATUS_SUCCESS;
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
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.utils.TemplateUtils;
import com.blas.blasnotification.properties.MailCredential;
import com.blas.blasnotification.properties.MailProperties;
import io.micrometer.core.instrument.Metrics;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
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
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public abstract class EmailService<T extends EmailRequest> {

  private static final String INTERNAL_SYSTEM_MSG = "Blas Email internal error";
  private static final String INVALID_EMAIL_MSG = "Invalid receiver email: %s";
  private static final String TOO_MANY_REQUEST_TO_BLAS_EMAIL_PLEASE_TRY_AGAIN = "Too many request to blas-notification. Please try again.";

  @Lazy
  protected final CentralizedLogService centralizedLogService;

  @Lazy
  protected final MailProperties mailProperties;

  @Lazy
  protected final TemplateUtils templateUtils;

  @Lazy
  @Resource(name = "needFieldMasks")
  private final Set<String> needFieldMasks;

  @Lazy
  private final MailSelectService mailSelectService;

  @Value("${blas.blas-notification.numberTryToSendEmailAgain}")
  protected int numberTryToSendEmailAgain;

  @Value("${blas.blas-notification.waitTimeFirstTryToSendEmailAgain}")
  protected long waitTimeFirstTryToSendEmailAgain;

  public void sendEmail(T emailRequest, List<EmailRequest> sentEmailList,
      List<EmailRequest> failedEmailList, ThreadPoolExecutor executor, CountDownLatch latch)
      throws IOException {
    MDC.put(EMAIL_LOG_ID, UUID.randomUUID().toString());

    String unkMessage = validateHeader(getEmailTemplate(emailRequest.getEmailTemplateName()),
        emailRequest.getData().keySet());

    MailCredential mailCredential = mailSelectService.getNextMailCredential();
    JavaMailSender javaMailSender = buildJavaEmailSender(mailCredential);
    MimeMessage message = javaMailSender.createMimeMessage();
    try {
      Map<String, String> contextMap = MDC.getCopyOfContextMap();
      executor.execute(() -> {
        if (contextMap != null) {
          MDC.setContextMap(contextMap);
        }
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
          helper.setFrom(mailCredential.getUsername());
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
          if (emailRequest instanceof HtmlEmailRequest) {
            message.setContent(htmlContent, "text/html; charset=UTF-8");
          }

          javaMailSender.send(message);
          emailRequest.setStatus(STATUS_SUCCESS);
          sentEmailList.add(emailRequest);
          Metrics.counter("blas.blas-notification.number-of-first-trying").increment();
        } catch (MailException | MessagingException mailException) {
          saveCentralizedLog(mailException, mailCredential, emailRequest, false);
          trySendingEmail(emailRequest, message, sentEmailList, failedEmailList);
        } catch (IOException ioException) {
          errorHandler(ioException, mailCredential, emailRequest, failedEmailList,
              ioException.getMessage());
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
      saveCentralizedLog(exception, mailCredential, emailRequest, false);
      failedEmailList.add(emailRequest);
      latch.countDown();
    }
  }

  protected abstract void addAttachmentToMail(MimeMessage message, T htmlEmailWithAttachmentRequest,
      AtomicBoolean isAddAttachFileCompletely, List<String> tempFileList,
      List<EmailRequest> failedEmailList, Map<String, String> data, String htmlContent)
      throws MessagingException, FileUploadException;

  private void saveCentralizedLog(Exception exception, MailCredential mailCredential,
      Object object, boolean sendEmail) {
    log.info("Email log ID: {}", MDC.get(EMAIL_LOG_ID));
    centralizedLogService.saveLog(exception,
        maskJsonWithFields(new JSONObject(mailCredential), needFieldMasks), object,
        maskJsonWithFields(new JSONObject(mailProperties), needFieldMasks), sendEmail);
  }

  private JavaMailSender buildJavaEmailSender(MailCredential mailCredential) {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(mailProperties.getHost());
    mailSender.setPort(mailProperties.getPort());
    mailSender.setUsername(mailCredential.getUsername());
    mailSender.setPassword(mailCredential.getPassword());

    Properties props = mailSender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtps");
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.ssl.enable", "true");
    props.put("mail.smtp.ssl.checkserveridentity", "true");
    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

    props.put("mail.smtp.connectiontimeout", "5000");
    props.put("mail.smtp.timeout", "5000");
    props.put("mail.smtp.writetimeout", "5000");
    return mailSender;
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
    while (attempts <= numberTryToSendEmailAgain) {
      MailCredential mailCredential = mailSelectService.getNextMailCredential();
      try {
        long waitTime = (long) (waitTimeFirstTryToSendEmailAgain
            + waitTimeFirstTryToSendEmailAgain * (attempts - 1) * 0.5);
        TimeUnit.MILLISECONDS.sleep(waitTime);
        JavaMailSender javaMailSender = buildJavaEmailSender(mailCredential);
        javaMailSender.send(message);
        sentEmailList.add(emailRequest);
        emailRequest.setStatus(STATUS_SUCCESS);
        emailRequest.setSentTime(now());
        return;
      } catch (MailException mailException) {
        saveCentralizedLog(mailException, mailCredential, emailRequest, false);
        attempts++;
      } catch (InterruptedException retryException) {
        saveCentralizedLog(retryException, mailCredential, emailRequest, false);
        attempts++;
        Thread.currentThread().interrupt();
      }
    }
    emailRequest.setReasonSendFailed(INTERNAL_SYSTEM_MSG);
    failedEmailList.add(emailRequest);
  }

  private void errorHandler(Exception exception, MailCredential mailCredential, T emailRequest,
      List<EmailRequest> failedEmailList, String errorMessage) {
    saveCentralizedLog(exception, mailCredential, emailRequest, true);
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
