package dev.payflow.apigateway;

import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Lets the Angular console (served from {@code localhost:4200}, a different origin from this
 * gateway) call it directly. Registered as a raw servlet {@link CorsFilter} — not a {@code
 * WebMvcConfigurer} — and ordered ahead of everything else so it runs, and fully answers a
 * preflight {@code OPTIONS}, before {@link dev.payflow.apigateway.security.JwtAuthenticationFilter}
 * ever sees the request; a {@code WebMvcConfigurer}-based CORS config only takes effect once a
 * request reaches Spring MVC's dispatcher, which a 401 written directly by that filter never does.
 */
@Configuration
public class CorsFilterConfiguration {

  @Bean
  public FilterRegistrationBean<CorsFilter> corsFilter() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:4200"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Force-Ledger-Failure", "X-Merchant-Id"));
    configuration.setExposedHeaders(List.of("Location", "Idempotency-Key"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
