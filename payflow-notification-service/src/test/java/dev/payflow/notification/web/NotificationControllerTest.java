package dev.payflow.notification.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.notification.delivery.WebhookDelivery;
import dev.payflow.notification.delivery.WebhookDeliveryRepository;
import dev.payflow.notification.delivery.WebhookDispatcherService;
import java.time.Instant;
import java.util.List;
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
  @MockitoBean private WebhookDeliveryRepository deliveryRepository;

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

  @Test
  void getMerchantDeliveriesReturnsThePageScopedToTheMerchantIdHeader() throws Exception {
    UUID merchantId = UUID.randomUUID();
    UUID deliveryId = UUID.randomUUID();
    UUID endpointId = UUID.randomUUID();
    when(deliveryRepository.findByMerchantId(merchantId, 0, 20))
        .thenReturn(
            List.of(
                new WebhookDelivery(
                    deliveryId, endpointId, "payment.captured", "{}", "DELIVERED", 1, 200, Instant.now(), null)));
    when(deliveryRepository.countByMerchantId(merchantId)).thenReturn(1L);

    mockMvc
        .perform(get("/webhooks/deliveries").header("X-Merchant-Id", merchantId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(deliveryId.toString()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));
  }

  @Test
  void getMerchantDeliveriesForwardsPageAndSizeParams() throws Exception {
    UUID merchantId = UUID.randomUUID();
    when(deliveryRepository.findByMerchantId(merchantId, 10, 5)).thenReturn(List.of());
    when(deliveryRepository.countByMerchantId(merchantId)).thenReturn(0L);

    mockMvc
        .perform(
            get("/webhooks/deliveries")
                .header("X-Merchant-Id", merchantId.toString())
                .param("page", "2")
                .param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    verify(deliveryRepository).findByMerchantId(merchantId, 10, 5);
  }

  @Test
  void getMerchantDeliveriesRequiresTheMerchantIdHeader() throws Exception {
    mockMvc
        .perform(get("/webhooks/deliveries"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }
}
