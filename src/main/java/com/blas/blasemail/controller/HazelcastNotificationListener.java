package com.blas.blasemail.controller;

import static com.blas.blascommon.constants.MessageTopic.BLAS_NOTIFICATION_QUEUE;

import com.blas.blascommon.configurations.ObjectMapperConfiguration;
import com.blas.blascommon.core.model.Notification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HazelcastNotificationListener {

  private final SimpMessagingTemplate messagingTemplate;

  private final ObjectMapperConfiguration objectMapperConfiguration;

  @Bean
  public int emailQueueListener(HazelcastInstance hazelcastInstance) {
    IQueue<String> queue = hazelcastInstance.getQueue(BLAS_NOTIFICATION_QUEUE);
    ItemListener<String> listener = new ItemListener<>() {
      @Override
      public void itemAdded(ItemEvent<String> itemEvent) {
        try {
          JSONArray arrayObject = new JSONArray(itemEvent.getItem());
          for (int index = 0; index < arrayObject.length(); index++) {
            JSONObject jsonObject = arrayObject.getJSONObject(index);
            Notification notification = objectMapperConfiguration.objectMapper()
                .readValue(jsonObject.toString(), Notification.class);
            messagingTemplate.convertAndSend(
                "/topic/notifications/" + notification.getReceiverUsername(), notification);
          }
        } catch (JsonProcessingException exception) {
          log.error(exception.toString());
        } finally {
          queue.poll();
        }
      }

      @Override
      public void itemRemoved(ItemEvent<String> itemEvent) {
        // no operation
      }
    };
    queue.addItemListener(listener, true);
    return 0;
  }
}
