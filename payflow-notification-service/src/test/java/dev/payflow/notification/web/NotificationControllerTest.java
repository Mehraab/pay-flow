package dev.payflow.notification.web;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.notification.delivery.WebhookDispatcherService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = NotificationController.class)
class NotificationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private WebhookDispatcherService dispatcherService;

  @Test
  void dispatchDelegatesToTheDispatcherServiceAndReturnsEnqueued() throws Exception {
    UUID merchantId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/internal/webhooks/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new DispatchWebhookRequest(
                            merchantId, "payment.captured", "{\"paymentId\":\"abc\"}"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ENQUEUED"));

    verify(dispatcherService)
        .dispatch(merchantId, "payment.captured", "{\"paymentId\":\"abc\"}");
  }
}
