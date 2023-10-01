package com.blas.blasemail.email;

import static com.blas.blascommon.utils.StringUtils.DOT;
import static com.blas.blasemail.constants.EmailConstant.STATUS_SUCCESS;
import static java.time.LocalDateTime.now;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.enums.EmailTemplate;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.utils.TemplateUtils;
import io.micrometer.core.instrument.Metrics;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HtmlEmail extends Email {

  public HtmlEmail(CentralizedLogService centralizedLogService,
      JavaMailSender javaMailSender,
      MailProperties mailProperties,
      TemplateUtils templateUtils, Set<String> needFieldMasks) {
    super(centralizedLogService, javaMailSender, mailProperties, templateUtils, needFieldMasks);
  }

  public void sendEmail(HtmlEmailRequest htmlEmailRequest, List<EmailRequest> sentEmailList,
      List<EmailRequest> failedEmailList, ThreadPoolExecutor executor, CountDownLatch latch)
      throws IOException {
    String unkMessage = validateHeader(
        EmailTemplate.valueOf(htmlEmailRequest.getEmailTemplateName()),
        htmlEmailRequest.getData().keySet());
    MimeMessage message = javaMailSender.createMimeMessage();
    executor.execute(() -> {
      if (isInvalidReceiverEmail(htmlEmailRequest, failedEmailList, latch)) {
        return;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
//      System.out.println(Thread.currentThread());
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
//        javaMailSender.send(message);
        htmlEmailRequest.setStatus(STATUS_SUCCESS);
        sentEmailList.add(htmlEmailRequest);
        Metrics.counter("blas.blas-email.number-of-first-trying").increment();
      } catch (MailException | MessagingException mailException) {
        trySendingEmail(htmlEmailRequest, message, sentEmailList, failedEmailList);
      } catch (IOException ioException) {
        errorHandler(ioException, htmlEmailRequest, failedEmailList, INTERNAL_SYSTEM_MSG);
      } catch (IllegalArgumentException illInterArgException) {
        errorHandler(illInterArgException, htmlEmailRequest, failedEmailList,
            INVALID_EMAIL_TEMPLATE);
      } finally {
        htmlEmailRequest.setSentTime(now());
        htmlEmailRequest.setReasonSendFailed(
            isEmpty(htmlEmailRequest.getReasonSendFailed()) ? unkMessage
                : htmlEmailRequest.getReasonSendFailed() + DOT + SPACE + unkMessage);
        System.out.println("lack");
        latch.countDown();
      }
    });
  }
}
