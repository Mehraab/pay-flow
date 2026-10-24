package dev.payflow.apigateway.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.apigateway.security.AuthService;
import dev.payflow.apigateway.security.InvalidTokenException;
import dev.payflow.apigateway.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private AuthService authService;
  // JwtAuthenticationFilter is a servlet Filter, so @WebMvcTest auto-registers it too — it needs
  // this bean even though AuthController itself (and /auth/** generally) bypasses the filter.
  @MockitoBean private JwtTokenService jwtTokenService;

  @Test
  void tokenReturns400WithAStructuredErrorForAnInvalidRole() throws Exception {
    mockMvc
        .perform(
            post("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TokenRequest("ADMIN", null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("role must be MERCHANT or OPERATOR"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/auth/token"));
  }

  @Test
  void tokenIssuesAnOperatorTokenWithNoMerchantId() throws Exception {
    when(authService.login(eq("ROLE_OPERATOR"), any()))
        .thenReturn(new TokenResponse("access-token", "refresh-token", "Bearer", 900));

    mockMvc
        .perform(
            post("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TokenRequest("OPERATOR", null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
  }

  @Test
  void refreshReturns401WithAStructuredErrorForAnInvalidToken() throws Exception {
    when(authService.refresh("bad-token")).thenThrow(new InvalidTokenException("Refresh token is invalid or expired"));

    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest("bad-token"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Refresh token is invalid or expired"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.path").value("/auth/refresh"));
  }

  @Test
  void refreshReturnsTheRotatedTokenPairOnSuccess() throws Exception {
    when(authService.refresh("old-token")).thenReturn(new TokenResponse("new-access", "new-refresh", "Bearer", 900));

    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest("old-token"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access"))
        .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
  }

  @Test
  void revokeReturnsRevokedStatus() throws Exception {
    mockMvc
        .perform(
            post("/auth/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest("some-token"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVOKED"));
  }
}
