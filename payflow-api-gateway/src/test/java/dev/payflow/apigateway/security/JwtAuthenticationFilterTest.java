package dev.payflow.apigateway.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class JwtAuthenticationFilterTest {

  private static final String SECRET = "test-secret-at-least-32-characters-long!!";

  private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, 900);
  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService, objectMapper);

  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;

  @BeforeEach
  void setUp() throws Exception {
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
  }

  @Test
  void publicAuthPathsPassThroughWithoutAToken() throws Exception {
    when(request.getRequestURI()).thenReturn("/auth/token");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(anyInt());
  }

  @Test
  void publicActuatorPathsPassThroughWithoutAToken() throws Exception {
    when(request.getRequestURI()).thenReturn("/actuator/health");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(anyInt());
  }

  @Test
  void protectedPathsWithoutATokenAreRejectedWith401() throws Exception {
    when(request.getRequestURI()).thenReturn("/api/v1/payments/abc-123");
    when(request.getHeader("Authorization")).thenReturn(null);

    filter.doFilter(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void operatorLedgerIntegrityWithAMerchantTokenIsForbidden() throws Exception {
    String token =
        jwtTokenService.issueAccessToken(UUID.randomUUID().toString(), "ROLE_MERCHANT", UUID.randomUUID());
    when(request.getRequestURI()).thenReturn("/operator/ledger/integrity");
    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

    filter.doFilter(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void operatorLedgerIntegrityWithAnOperatorTokenPassesThrough() throws Exception {
    String token = jwtTokenService.issueAccessToken("operator-1", "ROLE_OPERATOR", null);
    when(request.getRequestURI()).thenReturn("/operator/ledger/integrity");
    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(any(), any());
    verify(response, never()).setStatus(anyInt());
  }
}
