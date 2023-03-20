package com.blas.blasemail.email;

import static com.blas.blascommon.enums.BlasService.BLAS_EMAIL;
import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.utils.ValidUtils.isValidEmail;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.payload.EmailRequest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

@Component
public class Email {

  protected static final String INVALID_EMAIL_MSG = "Invalid receiver email: %s";

  public static final String INTERNAL_SYSTEM_MSG = "Blas Email internal error";

  @Value("${blas.blas-idp.isSendEmailAlert}")
  protected boolean isSendEmailAlert;

  @Autowired
  protected CentralizedLogService centralizedLogService;

  @Autowired
  protected TemplateEngine templateEngine;

  @Autowired
  protected JavaMailSender javaMailSender;

  @Autowired
  protected MailProperties mailProperties;

  protected void saveCentralizeLog(Exception e, Object object) {
    centralizedLogService.saveLog(BLAS_EMAIL.getServiceName(), ERROR, e.toString(),
        e.getCause() == null ? EMPTY : e.getCause().toString(),
        new JSONObject(javaMailSender).toString(), new JSONObject(object).toString(),
        new JSONObject(mailProperties).toString(), String.valueOf(new JSONArray(e.getStackTrace())),
        isSendEmailAlert);
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
}
