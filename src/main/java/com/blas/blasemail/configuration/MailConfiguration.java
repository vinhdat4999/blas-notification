package com.blas.blasemail.configuration;

import static com.blas.blascommon.exceptions.BlasErrorCodeEnum.MSG_BLAS_APP_FAILURE;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.blas.blascommon.exceptions.types.BlasException;
import com.blas.blasemail.properties.MailCredential;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@RequiredArgsConstructor
public class MailConfiguration {

  private static final TypeReference<List<MailCredential>> MAIL_CREDENTIAL_TYPE_REFERENCE = new TypeReference<>() {
  };

  private static final String CAN_NOT_GET_MAIL_CREDENTIALS = "Can not get mail credentials";
  private static final String EMPTY_MAIL_CREDENTIALS = "Empty mail credentials";

  @Lazy
  private final ObjectMapper objectMapper;

  @Bean
  public List<MailCredential> mailCredentialMap(
      @Value("${blas.blas-email.mail.credentials}") String credentials) {
    try {
      List<MailCredential> mailCredentials = objectMapper.readValue(credentials,
              MAIL_CREDENTIAL_TYPE_REFERENCE).stream()
          .filter(Objects::nonNull)
          .filter(MailCredential::isActive)
          .toList();
      if (isEmpty(mailCredentials)) {
        throw new BlasException(MSG_BLAS_APP_FAILURE, EMPTY_MAIL_CREDENTIALS);
      }
      return mailCredentials;
    } catch (JsonProcessingException exception) {
      throw new BlasException(MSG_BLAS_APP_FAILURE, CAN_NOT_GET_MAIL_CREDENTIALS, exception);
    }
  }

}
