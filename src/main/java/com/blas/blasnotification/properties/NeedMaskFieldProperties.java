package com.blas.blasnotification.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "blas.blas-notification")
public class NeedMaskFieldProperties {

  private String[] needMaskFields;
}
