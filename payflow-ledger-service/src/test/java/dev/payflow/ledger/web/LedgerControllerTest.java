package dev.payflow.ledger.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.ledger.account.AccountType;
import dev.payflow.ledger.account.LedgerAccount;
import dev.payflow.ledger.account.LedgerAccountService;
import dev.payflow.ledger.account.NormalBalance;
import dev.payflow.ledger.posting.LedgerPostingService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = LedgerController.class)
class LedgerControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private LedgerPostingService postingService;
  @MockitoBean private LedgerAccountService accountService;

  @Test
  void postCaptureDelegatesToThePostingServiceAndReturnsPosted() throws Exception {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/internal/ledger/post-capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new PostCaptureRequest(paymentId, merchantId, 21_400, "USD"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("POSTED"));

    verify(postingService)
        .postPaymentCapture(eq(paymentId), eq(merchantId), eq(Money.of(21_400, Currency.USD)));
  }

  @Test
  void platformAccountBalanceReturnsTheStoredBalance() throws Exception {
    LedgerAccount platformBank =
        new LedgerAccount(
            UUID.randomUUID(), null, AccountType.ASSET, "Platform Bank", NormalBalance.DEBIT, 21_400);
    when(accountService.findPlatformAccount("Platform Bank")).thenReturn(Optional.of(platformBank));

    mockMvc
        .perform(get("/accounts/{name}/balance", "Platform Bank"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountName").value("Platform Bank"))
        .andExpect(jsonPath("$.balanceMinor").value(21_400));
  }

  @Test
  void merchantAccountBalanceReturnsTheStoredBalance() throws Exception {
    UUID merchantId = UUID.randomUUID();
    LedgerAccount merchantPayable =
        new LedgerAccount(
            UUID.randomUUID(),
            merchantId,
            AccountType.LIABILITY,
            "Merchant Payable",
            NormalBalance.CREDIT,
            20_779);
    when(accountService.findMerchantAccount(merchantId, "Merchant Payable"))
        .thenReturn(Optional.of(merchantPayable));

    mockMvc
        .perform(get("/accounts/{merchantId}/{name}/balance", merchantId, "Merchant Payable"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountName").value("Merchant Payable"))
        .andExpect(jsonPath("$.balanceMinor").value(20_779));
  }
}
