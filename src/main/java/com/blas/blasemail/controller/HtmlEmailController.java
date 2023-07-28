package com.blas.blasemail.controller;

import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.HtmlEmailResponse;
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
public class HtmlEmailController extends EmailController {

  @PostMapping(value = "/html")
  public ResponseEntity<HtmlEmailResponse> sendHtmlEmailHandler(
      @RequestBody List<HtmlEmailRequest> htmlEmailPayloadList, Authentication authentication) {
    return sendHtmlEmail(htmlEmailPayloadList, authentication);
  }
}
