package com.blas.blasemail.controller;

import static java.time.LocalDateTime.now;

import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blascommon.payload.HtmlEmailWithAttachmentResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "/send-email")
public class HtmlEmailWithAttachmentController extends EmailController {

  @PostMapping(value = "/html-with-attachment")
  public ResponseEntity<HtmlEmailWithAttachmentResponse> sendHtmlWithFilesEmailHandler(
      @RequestBody List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestPayloadList,
      Authentication authentication) {
    setUpBeforeSendEmail(authentication, htmlEmailWithAttachmentRequestPayloadList);
    htmlEmailWithAttachmentRequestPayloadList.forEach(
        email -> htmlWithAttachmentEmail.sendEmail(email, sentEmailList, failedEmailList, latch));
    try {
      latch.await();
    } catch (InterruptedException e) {
      saveCentralizedLog(e, authentication, htmlEmailWithAttachmentRequestPayloadList);
      Thread.currentThread().interrupt();
      throw new BadRequestException(INTERNAL_SYSTEM_ERROR_MSG);
    }
    emailLogService.createEmailLog(
        buildEmailLog(failedEmailList.size(), failedEmailList, sentEmailList.size(),
            sentEmailList));
    return ResponseEntity.ok(
        HtmlEmailWithAttachmentResponse.builder().failedEmailNum(failedEmailList.size())
            .failedEmailList(failedEmailList).sentEmailNum(sentEmailList.size())
            .sentEmailList(sentEmailList).generatedBy(authentication.getName()).generatedTime(now())
            .build());
  }
}
