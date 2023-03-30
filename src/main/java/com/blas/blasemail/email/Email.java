package com.blas.blasemail.email;

import static com.blas.blascommon.enums.BlasService.BLAS_EMAIL;
import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.utils.JsonUtils.maskJsonObjectWithFields;
import static com.blas.blascommon.utils.ValidUtils.isValidEmail;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.payload.EmailRequest;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

@Component
public class Email {

  public static final String INTERNAL_SYSTEM_MSG = "Blas Email internal error";
  protected static final String INVALID_EMAIL_MSG = "Invalid receiver email: %s";
  @Value("${blas.blas-idp.isSendEmailAlert}")
  protected boolean isSendEmailAlert;

  @Value("${blas.blas-email.numberTryToSendEmailAgain}")
  protected int numberTryToSendEmailAgain;

  @Value("${blas.blas-email.waitTimeFirstTryToSendEmailAgain}")
  protected long waitTimeFirstTryToSendEmailAgain;

  @Lazy
  @Autowired
  protected CentralizedLogService centralizedLogService;

  @Lazy
  @Autowired
  protected TemplateEngine templateEngine;

  @Lazy
  @Autowired
  protected JavaMailSender javaMailSender;

  @Lazy
  @Autowired
  protected MailProperties mailProperties;

  @Lazy
  @Autowired
  private Set<String> needFieldMasks;

  protected void saveCentralizeLog(Exception e, Object object) {
    centralizedLogService.saveLog(BLAS_EMAIL.getServiceName(), ERROR, e.toString(),
        e.getCause() == null ? EMPTY : e.getCause().toString(),
        maskJsonObjectWithFields(new JSONObject(javaMailSender), needFieldMasks).toString(),
        new JSONObject(object).toString(),
        maskJsonObjectWithFields(new JSONObject(mailProperties), needFieldMasks).toString(),
        new JSONArray(e.getStackTrace()).toString(), isSendEmailAlert);
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
        Thread.sleep(waitTime);
        javaMailSender.send(message);
        sentEmailList.add(emailRequest);
        return;
      } catch (MailException | InterruptedException retryException) {
        exception = retryException;
        attempts++;
      }
    }
    emailRequest.setReasonSendFailed(INTERNAL_SYSTEM_MSG);
    saveCentralizeLog(exception, emailRequest);
    failedEmailList.add(emailRequest);
  }
}
