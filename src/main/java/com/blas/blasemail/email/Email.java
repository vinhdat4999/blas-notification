package com.blas.blasemail.email;

import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.exceptions.BlasErrorCodeEnum.MSG_FORMATTING_ERROR;
import static com.blas.blascommon.utils.JsonUtils.maskJsonObjectWithFields;
import static com.blas.blascommon.utils.StringUtils.COMMA;
import static com.blas.blascommon.utils.ValidUtils.isValidEmail;
import static com.blas.blasemail.constants.EmailConstant.STATUS_SUCCESS;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.time.LocalDateTime.now;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.enums.EmailTemplate;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.utils.TemplateUtils;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Email {

  public static final String INTERNAL_SYSTEM_MSG = "Blas Email internal error";
  protected static final String INVALID_EMAIL_MSG = "Invalid receiver email: %s";
  protected static final String INVALID_EMAIL_TEMPLATE = "Email template not found";
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
  @Value("${blas.service.serviceName}")
  private String serviceName;

  protected void saveCentralizeLog(Exception exception, Object object) {
    centralizedLogService.saveLog(serviceName, ERROR, exception.toString(),
        Optional.ofNullable(exception.getCause())
            .map(Throwable::toString)
            .orElse(EMPTY),
        maskJsonObjectWithFields(new JSONObject(javaMailSender), needFieldMasks).toString(),
        new JSONObject(object).toString(),
        maskJsonObjectWithFields(new JSONObject(mailProperties), needFieldMasks).toString(),
        new JSONArray(exception.getStackTrace()).toString(), isSendEmailAlert);
  }

  protected boolean isInvalidReceiverEmail(EmailRequest emailRequest,
      List<EmailRequest> failedEmailList, CountDownLatch latch) {
    if (isValidEmail(emailRequest.getEmailTo())) {
      return false;
    }
    emailRequest.setReasonSendFailed(format(INVALID_EMAIL_MSG, emailRequest.getEmailTo()));
    failedEmailList.add(emailRequest);
    latch.countDown();
    return true;
  }

  protected void trySendingEmail(EmailRequest emailRequest, MimeMessage message,
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
    saveCentralizeLog(exception, emailRequest);
    failedEmailList.add(emailRequest);
  }

  protected void errorHandler(Exception exception, EmailRequest emailRequest,
      List<EmailRequest> failedEmailList, String errorMessage) {
    saveCentralizeLog(exception, emailRequest);
    emailRequest.setReasonSendFailed(errorMessage);
    failedEmailList.add(emailRequest);
  }

  protected String validateHeader(EmailTemplate emailTemplate, Set<String> variables)
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
