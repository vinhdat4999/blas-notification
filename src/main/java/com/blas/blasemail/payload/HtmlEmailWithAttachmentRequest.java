package com.blas.blasemail.payload;

import lombok.Data;

@Data
public class HtmlEmailWithAttachmentRequest {

  private String emailTo;
  private String title;
  private String content;
  private String base64FileContent;
}