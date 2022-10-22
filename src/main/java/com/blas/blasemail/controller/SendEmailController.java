package com.blas.blasemail.controller;

import static com.blas.blascommon.utils.StringUtils.isBlank;
import static com.blas.blascommon.utils.ValidUtils.isValidEmail;

import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blasemail.email.HtmlEmail;
import com.blas.blasemail.email.HtmlWithAttachmentEmail;
import com.blas.blasemail.payload.HtmlEmailRequest;
import com.blas.blasemail.payload.HtmlEmailWithAttachmentRequest;
import com.blas.blasemail.payload.HtmlEmailWithAttachmentResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/send-email")
public class SendEmailController {

  private static final String EMAILS_INVALID = "All email unsent. These email is invalid: ";
  private static final String CONTAIN_NO_NAME_FILE = "Have 1 or more emails containing unnamed files. Please double check.";

  @Autowired
  private HtmlEmail htmlEmail;

  @Autowired
  private HtmlWithAttachmentEmail htmlWithAttachmentEmail;

  @PostMapping(value = "/html")
  public ResponseEntity<HtmlEmailResponse> sendHtmlEmailHandler(
      @RequestBody List<HtmlEmailRequest> htmlEmailPayloadList) {
    StringBuilder invalidEmails = new StringBuilder("");
    htmlEmailPayloadList.forEach(htmlEmailPayload -> {
      if (!isValidEmail(htmlEmailPayload.getEmailTo())) {
        invalidEmails.append(htmlEmailPayload.getEmailTo()).append(", ");
      }
    });
    if (!isBlank(invalidEmails.toString())) {
      throw new BadRequestException(
          EMAILS_INVALID + invalidEmails.substring(0, invalidEmails.length() - 2));
    }
    return ResponseEntity.ok(htmlEmail.sendEmail(htmlEmailPayloadList));
  }

  @PostMapping(value = "/html-with-attachment")
  public ResponseEntity<HtmlEmailWithAttachmentResponse> sendHtmlWithFilesEmailHandler(
      @RequestBody List<HtmlEmailWithAttachmentRequest> htmlEmailWithAttachmentRequestPayloadList) {
    StringBuilder invalidEmails = new StringBuilder("");
    htmlEmailWithAttachmentRequestPayloadList.forEach(htmlEmailWithAttachmentPayload -> {
      if (!isValidEmail(htmlEmailWithAttachmentPayload.getEmailTo())) {
        invalidEmails.append(htmlEmailWithAttachmentPayload.getEmailTo()).append(", ");
      }
      if (isBlank(htmlEmailWithAttachmentPayload.getFileName())) {
        throw new BadRequestException(CONTAIN_NO_NAME_FILE);
      }
    });
    if (!isBlank(invalidEmails.toString())) {
      throw new BadRequestException(
          EMAILS_INVALID + invalidEmails.substring(0, invalidEmails.length() - 2));
    }
    return ResponseEntity.ok(
        htmlWithAttachmentEmail.sendEmail(htmlEmailWithAttachmentRequestPayloadList));
  }
}