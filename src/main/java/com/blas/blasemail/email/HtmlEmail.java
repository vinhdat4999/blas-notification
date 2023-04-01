package com.blas.blasemail.email;

import com.blas.blascommon.enums.EmailTemplate;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
        String htmlContent = templateUtils.generateHtmlContent(
            EmailTemplate.valueOf(htmlEmailRequest.getEmailTemplateName()),
            htmlEmailRequest.getData());
        helper.setText(htmlContent, true);
        javaMailSender.send(message);
        sentEmailList.add(htmlEmailRequest);
      } catch (MailException | MessagingException mailException) {
        trySendingEmail(htmlEmailRequest, message, sentEmailList, failedEmailList);
      } catch (IOException ioException) {
        errorHandler(ioException, htmlEmailRequest, failedEmailList, INTERNAL_SYSTEM_MSG);
      } catch (IllegalArgumentException illArgException) {
        errorHandler(illArgException, htmlEmailRequest, failedEmailList, INVALID_EMAIL_TEMPLATE);
      } finally {
        latch.countDown();
      }
    }).start();
  }
}
