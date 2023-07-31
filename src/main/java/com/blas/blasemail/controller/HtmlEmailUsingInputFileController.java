package com.blas.blasemail.controller;

import static com.blas.blascommon.utils.fileutils.importfile.Excel.importFromExcel;
import static com.blas.blasemail.constants.EmailConstant.STATUS_FAILED;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.HtmlEmailResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping(value = "/send-email")
public class HtmlEmailUsingInputFileController extends EmailController {

  private static final String COLUMN_EMAIL_TO_NOT_FOUND = "Column emailTo not found";
  private static final String COLUMN_TITLE_TO_NOT_FOUND = "Column title not found";
  private static final String COLUMN_EMAIL_TEMPLATE_NAME_TO_NOT_FOUND = "Column emailTemplateName not found";

  @PostMapping(value = "/html-by-excel")
  public ResponseEntity<HtmlEmailResponse> sendEmailByExcelFile(
      @RequestParam("email-file") MultipartFile multipartFile, Authentication authentication)
      throws IOException {

    List<String[]> data = importFromExcel(multipartFile.getInputStream(),
        Objects.requireNonNull(multipartFile.getOriginalFilename()));
    Map<String, Integer> headerMap = new HashMap<>();
    String[] headers = data.get(0);
    List<HtmlEmailRequest> htmlEmailRequests = new ArrayList<>();
    for (int index = 0; index < headers.length; index++) {
      headerMap.put(headers[index], index);
    }
    for (int index = 1; index < data.size(); index++) {
      String[] lineData = data.get(index);
      if (lineData.length == 0) {
        continue;
      }
      htmlEmailRequests.add(buildHtmlEmailRequest(headerMap, lineData, headers));
    }
    return sendHtmlEmail(htmlEmailRequests, authentication);
  }

  private HtmlEmailRequest buildHtmlEmailRequest(Map<String, Integer> headerMap,
      String[] lineData, String[] headers) {
    if (!headerMap.containsKey(EMAIL_TO)) {
      throw new BadRequestException(COLUMN_EMAIL_TO_NOT_FOUND);
    }
    String emailTo = lineData[headerMap.get(EMAIL_TO)];
    if (!headerMap.containsKey(TITLE)) {
      throw new BadRequestException(COLUMN_TITLE_TO_NOT_FOUND);
    }
    String title = lineData[headerMap.get(TITLE)];
    if (!headerMap.containsKey(EMAIL_TEMPLATE_NAME)) {
      throw new BadRequestException(COLUMN_EMAIL_TEMPLATE_NAME_TO_NOT_FOUND);
    }
    String emailTemplateName = lineData[headerMap.get(EMAIL_TEMPLATE_NAME)];
    Map<String, String> variables = new HashMap<>();
    for (int subIndex = 0; subIndex < headers.length; subIndex++) {
      if (subIndex != headerMap.get(EMAIL_TO) && subIndex != headerMap.get(TITLE)
          && subIndex != headerMap.get(EMAIL_TEMPLATE_NAME)) {
        variables.put(headers[subIndex], subIndex < lineData.length ? lineData[subIndex] : EMPTY);
      }
    }
    return new HtmlEmailRequest(emailTo, title, emailTemplateName, variables, null, STATUS_FAILED, null);
  }
}
