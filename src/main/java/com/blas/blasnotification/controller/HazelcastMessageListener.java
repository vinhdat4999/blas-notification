package com.blas.blasnotification.controller;

import static com.blas.blascommon.constants.MessageTopic.BLAS_EMAIL_QUEUE;
import static com.blas.blascommon.utils.JsonUtils.maskJsonWithFields;

import com.blas.blascommon.core.model.EmailLog;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.deserializers.PairJsonDeserializer;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.payload.EmailRequest;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blasnotification.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HazelcastMessageListener extends EmailController<EmailRequest> {

  private static final String FILE_LIST_KEY = "fileList";

  private final EmailService<HtmlEmailRequest> htmlEmailService;
  private final EmailService<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentService;

  public HazelcastMessageListener(CentralizedLogService centralizedLogService,
      EmailService<HtmlEmailRequest> htmlEmailService,
      EmailService<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentService,
      EmailLogService emailLogService,
      @Qualifier("getAsyncExecutor") ThreadPoolTaskExecutor taskExecutor,
      AuthUserService authUserService, Set<String> needFieldMasks) {
    super(centralizedLogService, null, emailLogService, taskExecutor, authUserService,
        needFieldMasks);
    this.htmlEmailService = htmlEmailService;
    this.htmlEmailWithAttachmentService = htmlEmailWithAttachmentService;
  }

  @Bean
  public int emailQueueListener(HazelcastInstance hazelcastInstance) {
    IQueue<String> queue = hazelcastInstance.getQueue(BLAS_EMAIL_QUEUE);
    taskExecutor.getThreadPoolExecutor().execute(() -> {
      while (!queue.isEmpty()) {
        try {
          log.info("Backup items are handling... Queue: {}", queue);
          sendEmail(queue.poll());
        } catch (IOException exception) {
          log.error(exception.toString());
        }
      }
    });
    ItemListener<String> listener = new ItemListener<>() {
      @Override
      public void itemAdded(ItemEvent<String> itemEvent) {
        try {
          log.info("Hazelcast email queue received new item. Queue: {}", queue);
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
    JSONArray arrayObject = new JSONArray(message);
    CountDownLatch latch = new CountDownLatch(arrayObject.length());
    SimpleModule module = new SimpleModule();
    module.addDeserializer(Pair.class, new PairJsonDeserializer());
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(module);
    for (int index = 0; index < arrayObject.length(); index++) {
      JSONObject jsonObject = arrayObject.getJSONObject(index);
      EmailRequest request;
      if (jsonObject.has(FILE_LIST_KEY)) {
        request = mapper.readValue(jsonObject.toString(), HtmlEmailWithAttachmentRequest.class);
        htmlEmailWithAttachmentService.sendEmail((HtmlEmailWithAttachmentRequest) request,
            sentEmailList, failedEmailList, taskExecutor.getThreadPoolExecutor(), latch);
      } else {
        request = mapper.readValue(jsonObject.toString(), HtmlEmailRequest.class);
        htmlEmailService.sendEmail((HtmlEmailRequest) request, sentEmailList, failedEmailList,
            taskExecutor.getThreadPoolExecutor(), latch);
      }
    }

    postProcessor(latch, sentEmailList, failedEmailList);
  }

  private void postProcessor(CountDownLatch latch, List<EmailRequest> sentEmailList,
      List<EmailRequest> failedEmailList) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BadRequestException(INTERNAL_SYSTEM_ERROR_MSG, exception);
    }
    EmailLog emailLog = emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(),
            maskJsonWithFields(new JSONArray(failedEmailList), needFieldMasks),
            sentEmailList.size(),
            maskJsonWithFields(new JSONArray(sentEmailList), needFieldMasks)), false);
    log.info("Email sending processed - email_log_id: {} - fileReport: null",
        emailLog.getEmailLogId());
  }
}
