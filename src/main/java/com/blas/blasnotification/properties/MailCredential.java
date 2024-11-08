package com.blas.blasnotification.properties;

import lombok.Data;

@Data
public class MailCredential {

  private String username;
  private String password;
  private boolean active;
}
