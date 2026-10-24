package dev.payflow.notification.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WebhookDlqController.class)
class WebhookDlqControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private WebhookDeliveryRepository deliveryRepository;
  @MockitoBean private WebhookDispatcherService dispatcherService;

  @Test
  void deadLetterDeliveriesReturnsAllDeadLetterRows() throws Exception {
    UUID id = UUID.randomUUID();
    UUID endpointId = UUID.randomUUID();
    when(deliveryRepository.findDeadLetter())
        .thenReturn(
            List.of(
                new WebhookDelivery(
                    id, endpointId, "payment.captured", "{}", "DEAD_LETTER", 5, 500, Instant.now(), null)));

    mockMvc
        .perform(get("/operator/webhooks/dlq"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id.toString()))
        .andExpect(jsonPath("$[0].status").value("DEAD_LETTER"));
  }

  @Test
  void deadLetterDeliveriesReturnsAnEmptyListWhenNoneExist() throws Exception {
    when(deliveryRepository.findDeadLetter()).thenReturn(List.of());

    mockMvc
        .perform(get("/operator/webhooks/dlq"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void retryDelegatesToTheDispatcherServiceAndReturnsTheUpdatedDelivery() throws Exception {
    UUID id = UUID.randomUUID();
    when(deliveryRepository.findById(id))
        .thenReturn(
            Optional.of(
                new WebhookDelivery(
                    id, UUID.randomUUID(), "payment.captured", "{}", "DELIVERED", 1, 200, Instant.now(), null)));

    mockMvc
        .perform(post("/operator/webhooks/dlq/{id}/retry", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"));

    verify(dispatcherService).retryDelivery(id);
  }

  @Test
  void retryReturnsNotFoundWhenTheDeliveryDoesNotExist() throws Exception {
    UUID id = UUID.randomUUID();
    doThrow(new NoSuchElementException("No webhook delivery with id '" + id + "'"))
        .when(dispatcherService)
        .retryDelivery(any());

    mockMvc
        .perform(post("/operator/webhooks/dlq/{id}/retry", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("No webhook delivery with id '" + id + "'"))
        .andExpect(jsonPath("$.status").value(404));
  }
}
