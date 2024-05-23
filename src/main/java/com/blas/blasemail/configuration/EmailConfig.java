package com.blas.blasemail.configuration;

import static com.blas.blascommon.constants.BlasConstant.BLAS_IDP_EMAIL_PASSWORD;
import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blascommon.security.SecurityUtils.getPrivateKeyAesFromCertificate;

import com.blas.blascommon.configurations.CertPasswordConfiguration;
import com.blas.blascommon.core.service.BlasConfigService;
import com.blas.blascommon.properties.BlasPrivateKeyProperties;
import com.blas.blasemail.properties.EmailConfigProperties;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Properties;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EmailConfig {

  @Lazy
  private final EmailConfigProperties emailConfigProperties;

  @Lazy
  private final BlasPrivateKeyProperties blasPrivateKeyProperties;

  @Lazy
  private final CertPasswordConfiguration certPasswordConfiguration;

  @Lazy
  private final BlasConfigService blasConfigService;

  @Bean
  public JavaMailSender javaMailSender() {
    String password;
    try {
      final String privateKey = getPrivateKeyAesFromCertificate(
          blasPrivateKeyProperties.getCertificate(),
          blasPrivateKeyProperties.getAliasBlasPrivateKey(),
          certPasswordConfiguration.getCertPassword());
      password = aesDecrypt(privateKey,
          blasConfigService.getConfigValueFromKey(BLAS_IDP_EMAIL_PASSWORD));
    } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException |
             UnrecoverableKeyException | IllegalBlockSizeException | BadPaddingException |
             InvalidAlgorithmParameterException | InvalidKeyException |
             NoSuchPaddingException exception) {
      password = emailConfigProperties.getPassword();
      log.error(exception.toString());
    }

    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(emailConfigProperties.getHost());
    mailSender.setPort(emailConfigProperties.getPort());
    mailSender.setUsername(emailConfigProperties.getUsername());
    mailSender.setPassword(password);

    Properties javaMailProperties = new Properties();
    javaMailProperties.putAll(emailConfigProperties.getProperties());
    mailSender.setJavaMailProperties(javaMailProperties);

    return mailSender;
  }
}
