package com.blas.blasemail.controller;

import static com.blas.blascommon.constants.MessageTopic.BLAS_EMAIL_QUEUE;

import com.blas.blascommon.core.model.EmailLog;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.deserializers.PairJsonDeserializer;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blasemail.email.HtmlEmail;
import com.blas.blasemail.email.HtmlWithAttachmentEmail;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.annotation.Bean;
import org.springframework.data.util.Pair;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HazelcastMessageListener extends EmailController {

  public HazelcastMessageListener(CentralizedLogService centralizedLogService, HtmlEmail htmlEmail,
      HtmlWithAttachmentEmail htmlWithAttachmentEmail, EmailLogService emailLogService,
      JavaMailSender javaMailSender, ThreadPoolTaskExecutor taskExecutor,
      AuthUserService authUserService) {
    super(centralizedLogService, htmlEmail, htmlWithAttachmentEmail, emailLogService,
        javaMailSender,
        taskExecutor, authUserService);
  }

  @Bean
  public int emailQueueListener(HazelcastInstance hazelcastInstance)
      throws IOException {
    IQueue<String> queue = hazelcastInstance.getQueue(BLAS_EMAIL_QUEUE);
    while (!queue.isEmpty()) {
      sendEmail(queue.poll());
    }
    ItemListener<String> listener = new ItemListener<>() {
      @Override
      public void itemAdded(ItemEvent<String> itemEvent) {
        try {
          sendEmail(itemEvent.getItem());
        } catch (IOException exception) {
          log.error(exception.toString());
        }
        queue.poll();
      }

      @Override
      public void itemRemoved(ItemEvent<String> itemEvent) {
        // no operation
      }
    };
    queue.addItemListener(listener, true);
    return 0;
  }

  private void sendEmail(String message) throws IOException {
    List<EmailRequest> sentEmailList = new CopyOnWriteArrayList<>();
    List<EmailRequest> failedEmailList = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    JSONObject obj = new JSONArray(message).getJSONObject(0);
    SimpleModule module = new SimpleModule();
    module.addDeserializer(Pair.class, new PairJsonDeserializer());
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(module);
    EmailRequest request;
    try {
      request = mapper.readValue(obj.toString(), HtmlEmailRequest.class);
    } catch (Exception exception) {
      request = mapper.readValue(obj.toString(), HtmlEmailWithAttachmentRequest.class);
    }
    if (request instanceof HtmlEmailRequest htmlEmailRequest) {
      htmlEmail.sendEmail(htmlEmailRequest, sentEmailList, failedEmailList,
          taskExecutor.getThreadPoolExecutor(), latch);
    } else {
      htmlWithAttachmentEmail.sendEmail((HtmlEmailWithAttachmentRequest) request, sentEmailList,
          failedEmailList, latch);
    }
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BadRequestException(INTERNAL_SYSTEM_ERROR_MSG, exception);
    }
    EmailLog emailLog = emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(), failedEmailList, sentEmailList.size(),
            sentEmailList));
    log.info(String.format("Sent email - email_log_id: %s - fileReport: null",
        emailLog.getEmailLogId()));
  }
}
