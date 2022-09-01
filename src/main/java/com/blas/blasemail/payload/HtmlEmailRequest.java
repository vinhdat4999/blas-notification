package com.blas.blasemail.payload;

import lombok.Data;

@Data
public class HtmlEmailRequest {

  private String emailTo;
  private String title;
  private String content;
}