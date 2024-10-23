package com.blas.blasemail.service;

import com.blas.blasemail.properties.MailCredential;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailSelectService {

  private static final String BLAS_EMAIL_SELECTION_INFO_MAP = "blas-email-selection-info-map";

  private final IMap<String, Integer> dataMap;

  @Lazy
  private final List<MailCredential> mailCredentialMap;

  @Autowired
  public MailSelectService(HazelcastInstance hazelcastInstance,
      List<MailCredential> mailCredentialMap) {
    this.dataMap = hazelcastInstance.getMap(BLAS_EMAIL_SELECTION_INFO_MAP);
    this.mailCredentialMap = mailCredentialMap;
  }

  public MailCredential getNextMailCredential() {

    int numberOfAccount = mailCredentialMap.size();
    if (numberOfAccount == 1) {
      return mailCredentialMap.getFirst();
    }

    int currentIndex;
    try {
      currentIndex = this.dataMap.get(BLAS_EMAIL_SELECTION_INFO_MAP);
    } catch (Exception exception) {
      currentIndex = 0;
    }

    int nextIndex = ++currentIndex % numberOfAccount;
    dataMap.put(BLAS_EMAIL_SELECTION_INFO_MAP, nextIndex);
    return mailCredentialMap.get(nextIndex);
  }
}
