package dev.payflow.apigateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GatewayRestClientConfiguration {

  @Bean
  RestClient restClient() {
    return RestClient.create();
  }
}
