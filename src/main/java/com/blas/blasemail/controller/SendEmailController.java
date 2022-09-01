package com.blas.blasemail.controller;

import com.blas.blascommon.utils.httprequest.PostRequest;
import com.blas.blasemail.email.HtmlEmail;
import com.blas.blasemail.email.HtmlWithAttachmentEmail;
import com.blas.blasemail.payload.HtmlEmailRequest;
import com.blas.blasemail.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blasemail.payload.HtmlEmailWithAttachmentResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.mail.MessagingException;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/send-email")
public class SendEmailController {

  private static final String emailServerUrl = "https://personal-mfmntojx.outsystemscloud.com/BLAS_System/rest/BlasEmail/html";

  @Autowired
  private HtmlEmail htmlEmail;

  @Autowired
  private HtmlWithAttachmentEmail htmlWithAttachmentEmail;

  @PostMapping(value = "/html")
  public ResponseEntity<?> sendHtmlEmailHandler(
      @RequestBody List<HtmlEmailRequest> htmlEmailPayloadList) {
    String response = PostRequest.sendPostRequestWithJsonArrayPayloadGetStringResponse(
        emailServerUrl, null, null,
        new JSONArray(htmlEmailPayloadList));
//    AtomicInteger sentEmailNum = new AtomicInteger();
//    List<HtmlEmailRequest> htmlEmailRequestFailedList = new ArrayList<>();
//    htmlEmailPayloadList.forEach(htmlEmailPayload -> {
//      try {
//        htmlEmail.sendEmail(htmlEmailPayload.getEmailTo(), htmlEmailPayload.getTitle(),
//            htmlEmailPayload.getContent());
//        sentEmailNum.getAndIncrement();
//      } catch (MessagingException exception) {
//        exception.printStackTrace();
//        htmlEmailRequestFailedList.add(htmlEmailPayload);
//      }
//    });
//    HtmlEmailResponse htmlEmailResponse = new HtmlEmailResponse();
//    htmlEmailResponse.setSentEmailNum(sentEmailNum.get());
//    htmlEmailResponse.setHtmlEmailRequestFailedList(htmlEmailRequestFailedList);
//    return ResponseEntity.ok(htmlEmailResponse);
    return ResponseEntity.ok(response);
  }

  @PostMapping(value = "/html-with-attachment")
  public ResponseEntity<?> sendHtmlWithFilesEmailHandler(
      @RequestBody List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestPayloadList) {
    AtomicInteger sentEmailNum = new AtomicInteger();
    List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestFailedList = new ArrayList<>();
    htmlEmailWithAttachmentRequestPayloadList.forEach(htmlEmailWithAttachmentRequestPayload -> {
      try {
        htmlWithAttachmentEmail.sendEmail(htmlEmailWithAttachmentRequestPayload.getEmailTo(),
            htmlEmailWithAttachmentRequestPayload.getTitle(),
            htmlEmailWithAttachmentRequestPayload.getContent(),
            htmlEmailWithAttachmentRequestPayload.getBase64FileContent());
        sentEmailNum.getAndIncrement();
      } catch (MessagingException exception) {
        htmlEmailWithAttachmentRequestFailedList.add(htmlEmailWithAttachmentRequestPayload);
      }
    });
    HtmlEmailWithAttachmentResponse htmlEmailWithAttachmentResponse = new HtmlEmailWithAttachmentResponse();
    htmlEmailWithAttachmentResponse.setSentEmailNum(sentEmailNum.get());
    htmlEmailWithAttachmentResponse.setHtmlEmailWithAttachmentRequestList(
        htmlEmailWithAttachmentRequestFailedList);
    return ResponseEntity.ok(htmlEmailWithAttachmentResponse);
  }
}