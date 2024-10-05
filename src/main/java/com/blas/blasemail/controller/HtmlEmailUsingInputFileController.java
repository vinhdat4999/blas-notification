package com.blas.blasemail.controller;

import static com.blas.blascommon.exceptions.BlasErrorCodeEnum.MSG_FORMATTING_ERROR;
import static com.blas.blascommon.utils.fileutils.importfile.Excel.importFromExcel;
import static com.blas.blasemail.constants.EmailConstant.STATUS_FAILED;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.core.service.EmailLogService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.payload.EmailResponse;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blasemail.service.EmailService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping(value = "/send-email")
public class HtmlEmailUsingInputFileController extends EmailController<HtmlEmailRequest> {

  private static final String COLUMN_EMAIL_TO_NOT_FOUND = "Column emailTo not found";
  private static final String COLUMN_TITLE_TO_NOT_FOUND = "Column title not found";
  private static final String COLUMN_EMAIL_TEMPLATE_NAME_TO_NOT_FOUND = "Column emailTemplateName not found";
  private static final String ALL_INPUT_RECORD_MUST_BE_SAME_EMAIL_TEMPLATE = "All input record must be same email template";

  public HtmlEmailUsingInputFileController(CentralizedLogService centralizedLogService,
      EmailService<HtmlEmailRequest> emailService, EmailLogService emailLogService,
      ThreadPoolTaskExecutor taskExecutor, AuthUserService authUserService) {
    super(centralizedLogService, emailService, emailLogService, taskExecutor,
        authUserService);
  }

  @PostMapping(value = "/html-by-excel")
  public ResponseEntity<EmailResponse> sendEmailByExcelFile(
      @RequestParam("email-file") MultipartFile multipartFile, Authentication authentication)
      throws IOException {
    List<HtmlEmailRequest> htmlEmailRequests = buildHtmlEmailRequests(multipartFile);
    return super.sendHtmlEmail(htmlEmailRequests, authentication, true);
  }

  private List<HtmlEmailRequest> buildHtmlEmailRequests(MultipartFile multipartFile)
      throws IOException {
    List<String[]> data = importFromExcel(multipartFile.getInputStream(),
        Objects.requireNonNull(multipartFile.getOriginalFilename()));
    Map<String, Integer> headerMap = new HashMap<>();
    String[] headers = data.getFirst();
    List<HtmlEmailRequest> htmlEmailRequests = new ArrayList<>();
    for (int index = 0; index < headers.length; index++) {
      headerMap.put(headers[index], index);
    }
    validateInput(headerMap);
    String emailTemplate = EMPTY;
    for (int index = 1; index < data.size(); index++) {
      String[] lineData = data.get(index);
      if (lineData.length == 0) {
        continue;
      }
      if (index == 1) {
        emailTemplate = lineData[headerMap.get(EMAIL_TEMPLATE_NAME)];
      } else {
        String tempEmailTemplate = lineData[headerMap.get(EMAIL_TEMPLATE_NAME)];
        if (!StringUtils.equals(emailTemplate, tempEmailTemplate)) {
          throw new BadRequestException(MSG_FORMATTING_ERROR,
              ALL_INPUT_RECORD_MUST_BE_SAME_EMAIL_TEMPLATE);
        }
      }
      htmlEmailRequests.add(buildHtmlEmailRequest(headerMap, lineData, headers, emailTemplate));
    }
    return htmlEmailRequests;
  }

  private void validateInput(Map<String, Integer> headerMap) {
    if (!headerMap.containsKey(EMAIL_TO)) {
      throw new BadRequestException(COLUMN_EMAIL_TO_NOT_FOUND);
    }
    if (!headerMap.containsKey(TITLE)) {
      throw new BadRequestException(COLUMN_TITLE_TO_NOT_FOUND);
    }
    if (!headerMap.containsKey(EMAIL_TEMPLATE_NAME)) {
      throw new BadRequestException(COLUMN_EMAIL_TEMPLATE_NAME_TO_NOT_FOUND);
    }
  }

  private HtmlEmailRequest buildHtmlEmailRequest(Map<String, Integer> headerMap,
      String[] lineData, String[] headers, String emailTemplate) {
    String emailTo = lineData[headerMap.get(EMAIL_TO)];
    String title = lineData[headerMap.get(TITLE)];
    Map<String, String> variables = new HashMap<>();
    for (int subIndex = 0; subIndex < headers.length; subIndex++) {
      if (subIndex != headerMap.get(EMAIL_TO) && subIndex != headerMap.get(TITLE)
          && subIndex != headerMap.get(EMAIL_TEMPLATE_NAME)) {
        variables.put(headers[subIndex], subIndex < lineData.length ? lineData[subIndex] : EMPTY);
      }
    }
    return new HtmlEmailRequest(emailTo, title, emailTemplate, variables, null, STATUS_FAILED,
        null);
  }
}
