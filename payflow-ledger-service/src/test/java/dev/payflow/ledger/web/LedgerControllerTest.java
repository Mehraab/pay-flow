package dev.payflow.ledger.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import dev.payflow.ledger.posting.LedgerEntry;
import dev.payflow.ledger.posting.LedgerPostingService;
import java.time.Instant;
import java.util.List;
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
  void postCaptureThrowsWhenForceFailureHeaderIsTrueAndNeverCallsThePostingService() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();

    // An uncaught RuntimeException with no @ResponseStatus/handler propagates out of
    // mockMvc.perform() as a ServletException rather than being converted to a response
    // status MockMvc can assert on directly — same reasoning as Milestone 1 Chunk 3's
    // identically-shaped test for the account-balance 404 path.
    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    post("/internal/ledger/post-capture")
                        .header("X-Force-Ledger-Failure", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                new PostCaptureRequest(paymentId, merchantId, 21_400, "USD")))))
        .hasRootCauseInstanceOf(IllegalStateException.class);

    verifyNoInteractions(postingService);
  }

  @Test
  void entriesForTransactionReturnsThePostingServicesEntries() throws Exception {
    UUID debitAccountId = UUID.randomUUID();
    UUID creditAccountId = UUID.randomUUID();
    Instant postedAt = Instant.parse("2026-01-01T00:00:00Z");
    LedgerEntry entry =
        new LedgerEntry(UUID.randomUUID(), "demo-ref", debitAccountId, creditAccountId, 20_779, postedAt);
    when(postingService.findEntriesForTransaction("demo-ref")).thenReturn(List.of(entry));

    mockMvc
        .perform(get("/ledger").param("transaction", "demo-ref"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].transactionRef").value("demo-ref"))
        .andExpect(jsonPath("$[0].amountMinor").value(20_779));
  }

  @Test
  void entriesForTransactionReturnsAnEmptyListWhenNoneExist() throws Exception {
    when(postingService.findEntriesForTransaction("missing-ref")).thenReturn(List.of());

    mockMvc
        .perform(get("/ledger").param("transaction", "missing-ref"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
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

  @Test
  void slugAccountBalanceFallsBackToTheUniqueMerchantAccountWhenNoPlatformAccountMatches()
      throws Exception {
    UUID merchantId = UUID.randomUUID();
    LedgerAccount merchantPayable =
        new LedgerAccount(
            UUID.randomUUID(),
            merchantId,
            AccountType.LIABILITY,
            "Merchant Payable",
            NormalBalance.CREDIT,
            20_779);
    when(accountService.findPlatformAccount("merchant-payable")).thenReturn(Optional.empty());
    when(accountService.findUniqueMerchantAccountByNameOrSlug("merchant-payable"))
        .thenReturn(Optional.of(merchantPayable));

    mockMvc
        .perform(get("/accounts/{name}/balance", "merchant-payable"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountName").value("Merchant Payable"))
        .andExpect(jsonPath("$.balanceMinor").value(20_779));
  }

  @Test
  void platformAccountBalanceReturnsNotFoundWhenAccountDoesNotExist() throws Exception {
    when(accountService.findPlatformAccount("nonexistent")).thenReturn(Optional.empty());
    when(accountService.findUniqueMerchantAccountByNameOrSlug("nonexistent")).thenReturn(Optional.empty());

    mockMvc.perform(get("/accounts/{name}/balance", "nonexistent")).andExpect(status().isNotFound());
  }

  @Test
  void merchantAccountBalanceReturnsNotFoundWhenAccountDoesNotExist() throws Exception {
    UUID merchantId = UUID.randomUUID();
    when(accountService.findMerchantAccount(merchantId, "merchant-payable")).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/accounts/{merchantId}/{name}/balance", merchantId, "merchant-payable"))
        .andExpect(status().isNotFound());
  }

  @Test
  void ledgerIntegrityReturnsBalancedWhenDebitsAndCreditsMatch() throws Exception {
    when(postingService.sumTotalDebits()).thenReturn(1_000_000L);
    when(postingService.sumTotalCredits()).thenReturn(1_000_000L);

    mockMvc
        .perform(get("/operator/ledger/integrity"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDebitsMinor").value(1_000_000))
        .andExpect(jsonPath("$.totalCreditsMinor").value(1_000_000))
        .andExpect(jsonPath("$.balanced").value(true));
  }

  @Test
  void ledgerIntegrityReturnsUnbalancedWhenDebitsAndCreditsDiffer() throws Exception {
    when(postingService.sumTotalDebits()).thenReturn(1_000_000L);
    when(postingService.sumTotalCredits()).thenReturn(999_999L);

    mockMvc.perform(get("/operator/ledger/integrity")).andExpect(jsonPath("$.balanced").value(false));
  }

  @Test
  void listLedgerEntriesReturnsAPageWithTheStatusFieldDefaultedToPosted() throws Exception {
    UUID debitAccountId = UUID.randomUUID();
    UUID creditAccountId = UUID.randomUUID();
    Instant postedAt = Instant.parse("2026-01-01T00:00:00Z");
    LedgerEntry entry =
        new LedgerEntry(UUID.randomUUID(), "demo-ref", debitAccountId, creditAccountId, 20_779, postedAt);
    when(postingService.findEntriesPaged(null, 0, 20)).thenReturn(List.of(entry));
    when(postingService.countEntriesSince(null)).thenReturn(1L);

    mockMvc
        .perform(get("/operator/ledger/entries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].transactionRef").value("demo-ref"))
        .andExpect(jsonPath("$.content[0].status").value("POSTED"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void listLedgerEntriesForwardsSincePageAndSizeParams() throws Exception {
    Instant since = Instant.parse("2026-01-01T00:00:00Z");
    when(postingService.findEntriesPaged(since, 2, 5)).thenReturn(List.of());
    when(postingService.countEntriesSince(since)).thenReturn(0L);

    mockMvc
        .perform(get("/operator/ledger/entries").param("since", since.toString()).param("page", "2").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    verify(postingService).findEntriesPaged(since, 2, 5);
  }

  @Test
  void merchantLedgerEntriesReturnsAPageOfTheMerchantsEntries() throws Exception {
    UUID merchantId = UUID.randomUUID();
    UUID debitAccountId = UUID.randomUUID();
    UUID creditAccountId = UUID.randomUUID();
    Instant postedAt = Instant.parse("2026-01-01T00:00:00Z");
    LedgerEntry entry =
        new LedgerEntry(UUID.randomUUID(), "demo-ref", debitAccountId, creditAccountId, 20_779, postedAt);
    when(postingService.findMerchantEntriesPaged(merchantId, 0, 20)).thenReturn(List.of(entry));
    when(postingService.countMerchantEntries(merchantId)).thenReturn(1L);

    mockMvc
        .perform(get("/ledger/merchants/{merchantId}/entries", merchantId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].transactionRef").value("demo-ref"))
        .andExpect(jsonPath("$.content[0].status").value("POSTED"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void merchantLedgerEntriesForwardsPageAndSizeParams() throws Exception {
    UUID merchantId = UUID.randomUUID();
    when(postingService.findMerchantEntriesPaged(merchantId, 1, 5)).thenReturn(List.of());
    when(postingService.countMerchantEntries(merchantId)).thenReturn(0L);

    mockMvc
        .perform(get("/ledger/merchants/{merchantId}/entries", merchantId).param("page", "1").param("size", "5"))
        .andExpect(status().isOk());

    verify(postingService).findMerchantEntriesPaged(merchantId, 1, 5);
  }

  @Test
  void merchantLedgerEntriesPassesWhenTheMerchantIdHeaderMatches() throws Exception {
    UUID merchantId = UUID.randomUUID();
    when(postingService.findMerchantEntriesPaged(merchantId, 0, 20)).thenReturn(List.of());
    when(postingService.countMerchantEntries(merchantId)).thenReturn(0L);

    mockMvc
        .perform(get("/ledger/merchants/{merchantId}/entries", merchantId).header("X-Merchant-Id", merchantId.toString()))
        .andExpect(status().isOk());
  }

  @Test
  void merchantLedgerEntriesReturns403WhenTheMerchantIdHeaderDoesNotMatch() throws Exception {
    UUID merchantId = UUID.randomUUID();
    UUID otherMerchantId = UUID.randomUUID();

    mockMvc
        .perform(get("/ledger/merchants/{merchantId}/entries", merchantId).header("X-Merchant-Id", otherMerchantId.toString()))
        .andExpect(status().isForbidden());
  }
}
