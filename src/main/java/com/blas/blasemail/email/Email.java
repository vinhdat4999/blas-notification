package com.blas.blasemail.email;

import static com.blas.blascommon.enums.BlasService.BLAS_EMAIL;
import static com.blas.blascommon.enums.LogType.ERROR;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.properties.EmailConfigurationProperties;
import java.util.List;
import java.util.Properties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class Email {

  @Value("${blas.blas-idp.isSendEmailAlert}")
  protected boolean isSendEmailAlert;

  @Autowired
  protected CentralizedLogService centralizedLogService;

  @Autowired
  protected EmailConfigurationProperties emailConfigurationProperties;

  protected void saveCentralizeLog(Exception e,
      HtmlEmailWithAttachmentRequest htmlEmailWithAttachmentPayload) {
    centralizedLogService.saveLog(BLAS_EMAIL.getServiceName(), ERROR, e.toString(),
        e.getCause() == null ? EMPTY : e.getCause().toString(),
        new JSONArray(List.of(emailConfigurationProperties)).toString(),
        new JSONObject(htmlEmailWithAttachmentPayload).toString(), null,
        String.valueOf(new JSONArray(e.getStackTrace())), isSendEmailAlert);
  }

  protected Properties buildEmailProperties() {
    Properties props = new Properties();
    props.put("mail.smtp.host", "smtp.gmail.com");
    props.put("mail.smtp.port", emailConfigurationProperties.getPortSender());
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.starttls.required", "true");
    props.put("mail.smtp.ssl.protocols", "TLSv1.2");
    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
    props.put("mail.smtp.ssl.checkserveridentity", "true");
    return props;
  }

}
