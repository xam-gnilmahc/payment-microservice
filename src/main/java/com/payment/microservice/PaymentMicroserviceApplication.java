package com.payment.microservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @SpringBootApplication = @Configuration + @ComponentScan + @EnableAutoConfiguration
// @EnableScheduling = required for @Scheduled methods to run at all. Spring Boot does NOT turn this
// on by itself, and without it Spring Session's expired-session cleanup silently never happens, so
// finished logins would pile up in the SPRING_SESSION table forever.
@SpringBootApplication
@EnableScheduling

public class PaymentMicroserviceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentMicroserviceApplication.class, args);
  }
}