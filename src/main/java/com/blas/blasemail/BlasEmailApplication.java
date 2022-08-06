package com.blas.blasemail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EntityScan("com.blas")
@ComponentScan(basePackages = {"com.blas"})
@EnableJpaRepositories(basePackages = {"com.blas"})
@SpringBootApplication
public class BlasEmailApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlasEmailApplication.class, args);
    }

}
