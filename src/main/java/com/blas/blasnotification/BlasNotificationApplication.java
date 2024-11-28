package com.blas.blasnotification;

import com.blas.blasloggingaspect.listener.CustomApplicationListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EntityScan("com.blas")
@ComponentScan(basePackages = {"com.blas"})
@EnableJpaRepositories(basePackages = {"com.blas.blascommon.core.dao.jpa"})
@EnableMongoRepositories(basePackages = "com.blas.blascommon.core.dao.mongodb")
@SpringBootApplication
public class BlasNotificationApplication {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(BlasNotificationApplication.class);
    app.addListeners(new CustomApplicationListener());
    app.run(args);
  }
}
