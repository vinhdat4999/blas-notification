package com.blas.blasnotification.utils;

import static com.blas.blasnotification.constants.EmailConstant.STATUS_FAILED;
import static com.blas.blasnotification.constants.EmailConstant.STATUS_SUCCESS;

import com.blas.blascommon.payload.EmailRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EmailUtils {

  public static void buildSendingResult(
      List<CompletableFuture<EmailRequest>> sendEmailTaskFutures,
      List<EmailRequest> sentEmailList, List<EmailRequest> failedEmailList) {
    CompletableFuture<Void> allFutures = CompletableFuture
        .allOf(sendEmailTaskFutures.toArray(new CompletableFuture[0]));

    CompletableFuture<List<EmailRequest>> allResults = allFutures.thenApply(v ->
        sendEmailTaskFutures.stream()
            .map(CompletableFuture::join)
            .toList());

    List<EmailRequest> results = allResults.join();

    for (EmailRequest emailRequest : results) {
      if (STATUS_SUCCESS.equals(emailRequest.getStatus())) {
        sentEmailList.add(emailRequest);
      } else {
        emailRequest.setStatus(STATUS_FAILED);
        failedEmailList.add(emailRequest);
      }
    }
  }
}
