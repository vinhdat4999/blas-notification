package com.blas.blasemail.email;

import static com.blas.blascommon.enums.BlasService.BLAS_EMAIL;
import static com.blas.blascommon.enums.LogType.ERROR;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.service.CentralizedLogService;
import java.util.Map;
import java.util.Map.Entry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

public class Email {

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

  protected String generateHtmlContent(String emailTemplateName, Map<String, String> data) {
    Context context = new Context();
    for (Entry<String, String> entry : data.entrySet()) {
      context.setVariable(entry.getKey(), entry.getValue());
    }
    return templateEngine.process(emailTemplateName, context);
  }
}
