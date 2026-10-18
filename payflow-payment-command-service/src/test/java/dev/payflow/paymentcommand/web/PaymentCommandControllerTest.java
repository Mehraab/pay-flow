package dev.payflow.paymentcommand.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.cqrs.bus.CommandBus;
import dev.payflow.paymentcommand.command.CapturePaymentCommand;
import dev.payflow.paymentcommand.command.CreatePaymentCommand;
import dev.payflow.paymentcommand.command.FailPaymentCommand;
import dev.payflow.paymentcommand.domain.PaymentId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = PaymentCommandController.class)
class PaymentCommandControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private CommandBus commandBus;

  @Test
  void createDispatchesACreatePaymentCommandAndReturns201() throws Exception {
    var merchantId = UUID.randomUUID();
    var generatedId = PaymentId.generate();
    when(commandBus.dispatch(any(CreatePaymentCommand.class))).thenReturn(generatedId);

    mockMvc
        .perform(
            post("/payments")
                .header("Idempotency-Key", "demo-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreatePaymentRequest(merchantId, 21400, "USD"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.paymentId").value(generatedId.value().toString()));

    verify(commandBus)
        .dispatch(
            new CreatePaymentCommand(
                merchantId, dev.payflow.common.domain.Money.of(21400, dev.payflow.common.domain.Currency.USD), "demo-001"));
  }

  @Test
  void captureDispatchesACapturePaymentCommandAndReturns200() throws Exception {
    var id = UUID.randomUUID();

    mockMvc
        .perform(
            post("/internal/payments/{id}/capture", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CaptureRequest("mock-ch-abc123"))))
        .andExpect(status().isOk());

    verify(commandBus).dispatch(new CapturePaymentCommand(new PaymentId(id), "mock-ch-abc123"));
  }

  @Test
  void failDispatchesAFailPaymentCommandAndReturns200() throws Exception {
    var id = UUID.randomUUID();

    mockMvc
        .perform(
            post("/internal/payments/{id}/fail", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FailRequest("Insufficient funds"))))
        .andExpect(status().isOk());

    verify(commandBus).dispatch(new FailPaymentCommand(new PaymentId(id), "Insufficient funds"));
  }
}
