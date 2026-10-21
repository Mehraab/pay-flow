package dev.payflow.paymentcommand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PaymentCommandServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentCommandServiceApplication.class, args);
  }
}
