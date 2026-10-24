package dev.payflow.apigateway.security;

import dev.payflow.common.dto.ErrorResponse;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Gateway-wide RBAC gate (BR-11/BR-12): every path except {@code /actuator/**} and {@code
 * /auth/**} needs a valid Bearer access token, {@code /operator/**} additionally needs {@code
 * ROLE_OPERATOR}, and a {@code ROLE_MERCHANT} token has its {@code merchantId} claim injected as
 * an {@code X-Merchant-Id} header for everything further down the filter chain.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String ROLE_CLAIM = "role";
  private static final String MERCHANT_ID_CLAIM = "merchantId";
  private static final String ROLE_OPERATOR = "ROLE_OPERATOR";
  private static final String ROLE_MERCHANT = "ROLE_MERCHANT";
  private static final String MERCHANT_ID_HEADER = "X-Merchant-Id";

  private final JwtTokenService jwtTokenService;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, ObjectMapper objectMapper) {
    this.jwtTokenService = jwtTokenService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      // CORS preflight — the browser never attaches Authorization to it, so this filter must
      // never 401 it; CorsFilterConfiguration's CorsFilter answers it before this filter runs.
      return true;
    }
    String path = request.getRequestURI();
    return path.startsWith("/actuator/") || path.startsWith("/auth/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid authorization token");
      return;
    }

    Claims claims;
    try {
      claims = jwtTokenService.parseAndValidateAccessToken(header.substring("Bearer ".length()));
    } catch (RuntimeException e) {
      writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid authorization token");
      return;
    }

    String role = claims.get(ROLE_CLAIM, String.class);
    if (request.getRequestURI().startsWith("/operator/") && !ROLE_OPERATOR.equals(role)) {
      writeError(request, response, HttpServletResponse.SC_FORBIDDEN, "Forbidden: Requires ROLE_OPERATOR");
      return;
    }

    if (ROLE_MERCHANT.equals(role)) {
      String merchantId = claims.get(MERCHANT_ID_CLAIM, String.class);
      filterChain.doFilter(new MerchantIdHeaderRequest(request, merchantId), response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private void writeError(HttpServletRequest request, HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response
        .getWriter()
        .write(objectMapper.writeValueAsString(ErrorResponse.of(message, status, request.getRequestURI())));
  }

  /** Makes the merchant id resolved from the token's claims visible as {@code X-Merchant-Id}. */
  private static final class MerchantIdHeaderRequest extends HttpServletRequestWrapper {

    private final String merchantId;

    MerchantIdHeaderRequest(HttpServletRequest request, String merchantId) {
      super(request);
      this.merchantId = merchantId;
    }

    @Override
    public String getHeader(String name) {
      if (MERCHANT_ID_HEADER.equalsIgnoreCase(name)) {
        return merchantId;
      }
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (MERCHANT_ID_HEADER.equalsIgnoreCase(name)) {
        return Collections.enumeration(List.of(merchantId));
      }
      return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      List<String> names = new ArrayList<>();
      super.getHeaderNames().asIterator().forEachRemaining(names::add);
      names.add(MERCHANT_ID_HEADER);
      return Collections.enumeration(names);
    }
  }
}
