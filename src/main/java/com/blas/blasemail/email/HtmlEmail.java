package com.blas.blasemail.email;

import static com.blas.blascommon.utils.TemplateUtils.generateHtmlContent;

import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailRequest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Async
@Service
public class HtmlEmail extends Email {

  public void sendEmail(HtmlEmailRequest htmlEmailRequest, List<EmailRequest> sentEmailList,
      List<EmailRequest> failedEmailList, CountDownLatch latch) {
    new Thread(() -> {
      if (isInvalidReceiverEmail(htmlEmailRequest, failedEmailList, latch)) {
        return;
      }
      MimeMessage message = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message);
      try {
        helper.setTo(htmlEmailRequest.getEmailTo());
        helper.setSubject(htmlEmailRequest.getTitle());
        String htmlContent = generateHtmlContent(templateEngine,
            htmlEmailRequest.getEmailTemplateName(), htmlEmailRequest.getData());
        helper.setText(htmlContent, true);
        javaMailSender.send(message);
        sentEmailList.add(htmlEmailRequest);
      } catch (MailException | MessagingException e) {
        htmlEmailRequest.setReasonSendFailed(INTERNAL_SYSTEM_MSG);
        saveCentralizeLog(e, htmlEmailRequest);
        failedEmailList.add(htmlEmailRequest);
      } finally {
        latch.countDown();
      }
    }).start();
  }
}
