package com.blas.blasemail.email;

import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.HtmlEmailResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Async
@Service
public class HtmlEmail extends Email {

  public HtmlEmailResponse sendEmail(List<HtmlEmailRequest> htmlEmailPayloadList)
      throws MessagingException {
    MimeMessage mail = javaMailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mail, true);
    helper.setFrom(new InternetAddress(mailProperties.getUsername()));
    AtomicInteger sentEmailNum = new AtomicInteger();
    List<HtmlEmailRequest> htmlEmailRequestFailedList = new ArrayList<>();
    htmlEmailPayloadList.forEach(htmlEmailPayload -> {
      try {
        String htmlContent = generateHtmlContent(htmlEmailPayload.getEmailTemplateName(),
            htmlEmailPayload.getData());
        helper.setTo(htmlEmailPayload.getEmailTo());
        helper.setSubject(htmlEmailPayload.getTitle());
        helper.setText(htmlContent, true);
        javaMailSender.send(mail);
        sentEmailNum.getAndIncrement();
      } catch (MessagingException e) {
        saveCentralizeLog(e, htmlEmailPayload);
        htmlEmailRequestFailedList.add(htmlEmailPayload);
      }
    });
    HtmlEmailResponse htmlEmailResponse = new HtmlEmailResponse();
    htmlEmailResponse.setSentEmailNum(sentEmailNum.get());
    htmlEmailResponse.setHtmlEmailRequestFailedList(htmlEmailRequestFailedList);
    return htmlEmailResponse;
  }
}
