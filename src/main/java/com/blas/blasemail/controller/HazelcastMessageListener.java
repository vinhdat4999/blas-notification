package com.blas.blasemail.controller;

import static com.blas.blascommon.constants.MessageTopic.BLAS_GROW_STATISTIC_TOPIC;
import static com.blas.blascommon.constants.ResponseMessage.CANNOT_CONNECT_TO_HOST;
import static com.blas.blascommon.constants.ResponseMessage.HTTP_STATUS_NOT_200;
import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.utils.httprequest.PostRequest.sendPostRequestWithJsonArrayPayload;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.exceptions.types.ServiceUnavailableException;
import com.blas.blascommon.jwt.JwtTokenUtil;
import com.blas.blascommon.payload.HttpResponse;
import com.blas.blascommon.properties.BlasEmailConfiguration;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HazelcastMessageListener {

  @Value("${blas.service.serviceName}")
  private String serviceName;

  @Lazy
  private final CentralizedLogService centralizedLogService;

  @Lazy
  private final JwtTokenUtil jwtTokenUtil;

  @Lazy
  private final BlasEmailConfiguration blasEmailConfiguration;

  public HazelcastMessageListener(HazelcastInstance hazelcastInstance,
      CentralizedLogService centralizedLogService, JwtTokenUtil jwtTokenUtil,
      BlasEmailConfiguration blasEmailConfiguration) {
    this.centralizedLogService = centralizedLogService;
    this.jwtTokenUtil = jwtTokenUtil;
    this.blasEmailConfiguration = blasEmailConfiguration;
    ITopic<String> topic = hazelcastInstance.getTopic(BLAS_GROW_STATISTIC_TOPIC);
    topic.addMessageListener(message -> {
      try {
        HttpResponse response = sendPostRequestWithJsonArrayPayload(
            this.blasEmailConfiguration.getEndpointHtmlEmailWithAttachments(), null,
            this.jwtTokenUtil.generateInternalSystemToken(),
            new JSONArray(message.getMessageObject()));
        if (response.getStatusCode() != HttpStatus.OK.value()) {
          throw new BadRequestException(HTTP_STATUS_NOT_200);
        }
      } catch (BadRequestException | IOException exception) {
        this.centralizedLogService.saveLog(serviceName, ERROR, exception.toString(),
            exception.getCause() == null ? EMPTY : exception.getCause().toString(),
            message.getMessageObject(), null, null,
            String.valueOf(new JSONArray(exception.getStackTrace())), false);
        throw new ServiceUnavailableException(CANNOT_CONNECT_TO_HOST);
      }
    });
  }
}
