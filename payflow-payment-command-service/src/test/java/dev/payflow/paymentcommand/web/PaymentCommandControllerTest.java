package dev.payflow.paymentcommand.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.cqrs.bus.CommandBus;
import dev.payflow.cqrs.exception.AggregateNotFoundException;
import dev.payflow.paymentcommand.command.CapturePaymentCommand;
import dev.payflow.paymentcommand.command.CreatePaymentCommand;
import dev.payflow.paymentcommand.command.FailPaymentCommand;
import dev.payflow.paymentcommand.command.RefundPaymentCommand;
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
                        new CreatePaymentRequest(merchantId, 21400, "USD", "4242424242424242", null, null))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.paymentId").value(generatedId.value().toString()));

    verify(commandBus)
        .dispatch(
            new CreatePaymentCommand(
                merchantId,
                dev.payflow.common.domain.Money.of(21400, dev.payflow.common.domain.Currency.USD),
                "4242424242424242",
                false,
                "demo-001",
                null));
  }

  @Test
  void createSetsForceLedgerFailureWhenTheHeaderIsPresent() throws Exception {
    var merchantId = UUID.randomUUID();
    var generatedId = PaymentId.generate();
    when(commandBus.dispatch(any(CreatePaymentCommand.class))).thenReturn(generatedId);

    mockMvc
        .perform(
            post("/payments")
                .header("Idempotency-Key", "demo-002")
                .header("X-Force-Ledger-Failure", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreatePaymentRequest(merchantId, 21400, "USD", "4242424242424242", null, null))))
        .andExpect(status().isCreated());

    verify(commandBus)
        .dispatch(
            new CreatePaymentCommand(
                merchantId,
                dev.payflow.common.domain.Money.of(21400, dev.payflow.common.domain.Currency.USD),
                "4242424242424242",
                true,
                "demo-002",
                null));
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

  @Test
  void internalRefundDispatchesARefundPaymentCommandAndReturns200() throws Exception {
    var id = UUID.randomUUID();

    mockMvc.perform(post("/internal/payments/{id}/refund", id)).andExpect(status().isOk());

    verify(commandBus).dispatch(new RefundPaymentCommand(new PaymentId(id)));
  }

  @Test
  void publicRefundDispatchesARefundPaymentCommandAndReturns200() throws Exception {
    var id = UUID.randomUUID();

    mockMvc.perform(post("/payments/{id}/refund", id)).andExpect(status().isOk());

    verify(commandBus).dispatch(new RefundPaymentCommand(new PaymentId(id)));
  }

  @Test
  void refundReturns409WhenThePaymentIsInAnInvalidStateForRefund() throws Exception {
    var id = UUID.randomUUID();
    when(commandBus.dispatch(new RefundPaymentCommand(new PaymentId(id))))
        .thenThrow(new IllegalStateException("Cannot refund payment " + id + " in status CREATED"));

    mockMvc
        .perform(post("/payments/{id}/refund", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Cannot refund payment " + id + " in status CREATED"))
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void captureReturns404WhenThePaymentDoesNotExist() throws Exception {
    var id = UUID.randomUUID();
    when(commandBus.dispatch(any(CapturePaymentCommand.class)))
        .thenThrow(new AggregateNotFoundException(new PaymentId(id)));

    mockMvc
        .perform(
            post("/internal/payments/{id}/capture", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CaptureRequest("mock-ch-abc123"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}
