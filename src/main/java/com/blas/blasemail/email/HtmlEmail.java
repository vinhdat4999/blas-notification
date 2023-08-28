package com.blas.blasemail.email;

import static com.blas.blascommon.utils.StringUtils.DOT;
import static com.blas.blasemail.constants.EmailConstant.STATUS_SUCCESS;
import static java.time.LocalDateTime.now;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.blas.blascommon.enums.EmailTemplate;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailRequest;
import io.micrometer.core.instrument.Metrics;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Async
@Service
public class HtmlEmail extends Email {

  public void sendEmail(HtmlEmailRequest htmlEmailRequest, List<EmailRequest> sentEmailList,
      List<EmailRequest> failedEmailList, CountDownLatch latch) throws IOException {
    String unkMessage = validateHeader(
        EmailTemplate.valueOf(htmlEmailRequest.getEmailTemplateName()),
        htmlEmailRequest.getData().keySet());
    MimeMessage message = javaMailSender.createMimeMessage();
    new Thread(() -> {
      if (isInvalidReceiverEmail(htmlEmailRequest, failedEmailList, latch)) {
        return;
      }
      if (isBlank(htmlEmailRequest.getEmailTemplateName())) {
        saveCentralizeLog(new NullPointerException(INVALID_EMAIL_TEMPLATE), htmlEmailRequest);
        htmlEmailRequest.setReasonSendFailed(INVALID_EMAIL_TEMPLATE);
        failedEmailList.add(htmlEmailRequest);
      }
      MimeMessageHelper helper = new MimeMessageHelper(message);
      try {
        helper.setTo(htmlEmailRequest.getEmailTo());
        helper.setSubject(htmlEmailRequest.getTitle());
        String htmlContent = templateUtils.generateHtmlContent(
            EmailTemplate.valueOf(htmlEmailRequest.getEmailTemplateName()),
            htmlEmailRequest.getData());
        helper.setText(htmlContent, true);
        javaMailSender.send(message);
        htmlEmailRequest.setStatus(STATUS_SUCCESS);
        sentEmailList.add(htmlEmailRequest);
        Metrics.counter("blas.blas-email.number-of-first-trying").increment();
      } catch (MailException | MessagingException mailException) {
        trySendingEmail(htmlEmailRequest, message, sentEmailList, failedEmailList);
      } catch (IOException ioException) {
        errorHandler(ioException, htmlEmailRequest, failedEmailList, INTERNAL_SYSTEM_MSG);
      } catch (IllegalArgumentException illArgException) {
        errorHandler(illArgException, htmlEmailRequest, failedEmailList, INVALID_EMAIL_TEMPLATE);
      } finally {
        htmlEmailRequest.setSentTime(now());
        htmlEmailRequest.setReasonSendFailed(
            isEmpty(htmlEmailRequest.getReasonSendFailed()) ? unkMessage
                : htmlEmailRequest.getReasonSendFailed() + DOT + SPACE + unkMessage);
        latch.countDown();
      }
    }).start();
  }
}
