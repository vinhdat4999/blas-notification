package com.blas.blasnotification.listener;

import static com.blas.blascommon.constants.MDCConstant.EMAIL_LOG_ID;
import static com.blas.blascommon.constants.MDCConstant.GLOBAL_ID;
import static com.blas.blascommon.constants.MessageTopic.BLAS_EMAIL_QUEUE;
import static com.blas.blascommon.utils.JsonUtils.maskJsonWithFields;
import static com.blas.blasnotification.utils.EmailUtils.buildSendingResult;
import static java.time.LocalDateTime.now;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.blas.blascommon.core.model.AuthUser;
import com.blas.blascommon.core.model.EmailLog;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.deserializers.PairJsonDeserializer;
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
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HazelcastMessageListener {

  private static final String FILE_LIST_KEY = "fileList";

  @Lazy
  private final EmailService<HtmlEmailRequest> htmlEmailService;

  @Lazy
  private final EmailService<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentService;

  @Lazy
  protected final EmailLogService emailLogService;

  @Lazy
  @Resource(name = "needFieldMasks")
  protected final Set<String> needFieldMasks;

  @Lazy
  private final AuthUserService authUserService;

  @Bean
  @Async
  public CompletableFuture<Void> emailQueueListener(HazelcastInstance hazelcastInstance) {
    IQueue<String> queue = hazelcastInstance.getQueue(BLAS_EMAIL_QUEUE);
    List<EmailRequest> sentEmailList = new CopyOnWriteArrayList<>();
    List<EmailRequest> failedEmailList = new CopyOnWriteArrayList<>();
    List<CompletableFuture<EmailRequest>> sendEmailTaskFutures = new ArrayList<>();
    while (!queue.isEmpty()) {
      try {
        log.info("Backup items are handling... Queue: {}", queue);
        sendEmail(queue.poll(), sendEmailTaskFutures);
      } catch (IOException exception) {
        log.error(exception.toString());
      }
    }

    if (isNotEmpty(sentEmailList) || isNotEmpty(failedEmailList)) {
      buildSendingResult(sendEmailTaskFutures, sentEmailList, failedEmailList);
      postProcessor(sentEmailList, failedEmailList);
    }

    ItemListener<String> listener = new ItemListener<>() {
      @Override
      public void itemAdded(ItemEvent<String> itemEvent) {
        List<EmailRequest> sentEmailList = new CopyOnWriteArrayList<>();
        List<EmailRequest> failedEmailList = new CopyOnWriteArrayList<>();
        List<CompletableFuture<EmailRequest>> sendEmailTaskFutures = new ArrayList<>();
        try {
          log.info("Hazelcast email queue received new item. Queue: {}", queue);
          sendEmail(itemEvent.getItem(), sendEmailTaskFutures);
        } catch (IOException exception) {
          log.error(exception.toString());
        }
        queue.poll();
        buildSendingResult(sendEmailTaskFutures, sentEmailList, failedEmailList);
        postProcessor(sentEmailList, failedEmailList);
      }

      @Override
      public void itemRemoved(ItemEvent<String> itemEvent) {
        // no operation
      }
    };
    queue.addItemListener(listener, true);
    return CompletableFuture.completedFuture(null);
  }

  private void sendEmail(String message, List<CompletableFuture<EmailRequest>> sendEmailTaskFutures)
      throws IOException {
    JSONArray arrayObject = new JSONArray(message);
    SimpleModule module = new SimpleModule();
    module.addDeserializer(Pair.class, new PairJsonDeserializer());
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(module);
    for (int index = 0; index < arrayObject.length(); index++) {
      JSONObject jsonObject = arrayObject.getJSONObject(index);
      EmailRequest request;

      if (jsonObject.has(FILE_LIST_KEY)) {
        request = mapper.readValue(jsonObject.toString(), HtmlEmailWithAttachmentRequest.class);
        CompletableFuture<EmailRequest> sendEmailTask = htmlEmailWithAttachmentService.sendEmail(
            (HtmlEmailWithAttachmentRequest) request);
        sendEmailTaskFutures.add(sendEmailTask);
      } else {
        request = mapper.readValue(jsonObject.toString(), HtmlEmailRequest.class);
        CompletableFuture<EmailRequest> sendEmailTask = htmlEmailService.sendEmail(
            (HtmlEmailRequest) request);
        sendEmailTaskFutures.add(sendEmailTask);
      }
    }
  }

  private void postProcessor(List<EmailRequest> sentEmailList, List<EmailRequest> failedEmailList) {
    EmailLog emailLog = emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(),
            maskJsonWithFields(new JSONArray(failedEmailList), needFieldMasks),
            sentEmailList.size(),
            maskJsonWithFields(new JSONArray(sentEmailList), needFieldMasks)), false);
    log.info("Email sending processed - email_log_id: {} - fileReport: null",
        emailLog.getEmailLogId());
  }

  private EmailLog buildEmailLog(int failedEmailNum, JSONArray failedEmailList, int sentEmailNum,
      JSONArray sentEmailList) {
    String username = "system";
    AuthUser generatedBy = authUserService.getAuthUserByUsername(username);

    String initEmailLogId = MDC.get(EMAIL_LOG_ID);
    if (isBlank(initEmailLogId)) {
      initEmailLogId = UUID.randomUUID().toString();
    }
    return EmailLog.builder()
        .emailLogId(initEmailLogId)
        .globalId(MDC.get(GLOBAL_ID))
        .authUser(generatedBy)
        .timeLog(now())
        .failedEmailNum(failedEmailNum)
        .failedEmailList(failedEmailList.toString())
        .sentEmailNum(sentEmailNum)
        .sentEmailList(sentEmailList.toString())
        .build();
  }
}
