package dev.payflow.ledger.posting;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.ledger.AbstractPostgresIntegrationTest;
import dev.payflow.ledger.account.LedgerAccount;
import dev.payflow.ledger.account.LedgerAccountService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SpringBootTest
class LedgerPostingServiceTest extends AbstractPostgresIntegrationTest {

  @Autowired private LedgerPostingService postingService;
  @Autowired private LedgerAccountService accountService;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  @Test
  void postingACaptureSplitsTheFeeAndUpdatesBothSidesOfBothLegs() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD); // $214.00

    postingService.postPaymentCapture(paymentId, merchantId, amount);

    LedgerAccount platformBank = accountService.findPlatformAccount("Platform Bank").orElseThrow();
    LedgerAccount feeRevenue =
        accountService.findPlatformAccount("Platform Fee Revenue").orElseThrow();
    LedgerAccount merchantPayable =
        accountService.findMerchantAccount(merchantId, "Merchant Payable").orElseThrow();

    assertThat(feeRevenue.balanceMinor()).isEqualTo(621);
    assertThat(merchantPayable.balanceMinor()).isEqualTo(20_779);
    assertThat(platformBank.balanceMinor()).isEqualTo(21_400);

    Integer entryCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE transaction_ref = :ref",
            new MapSqlParameterSource("ref", paymentId.toString()),
            Integer.class);
    assertThat(entryCount).isEqualTo(2);
  }

  @Test
  void postingTheSamePaymentTwiceIsIdempotent() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);

    postingService.postPaymentCapture(paymentId, merchantId, amount);
    postingService.postPaymentCapture(paymentId, merchantId, amount);

    LedgerAccount merchantPayable =
        accountService.findMerchantAccount(merchantId, "Merchant Payable").orElseThrow();
    assertThat(merchantPayable.balanceMinor()).isEqualTo(20_779);

    Integer entryCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE transaction_ref = :ref",
            new MapSqlParameterSource("ref", paymentId.toString()),
            Integer.class);
    assertThat(entryCount).isEqualTo(2);
  }

  @Test
  void twoDifferentMerchantsGetSeparateMerchantPayableAccounts() {
    UUID firstMerchant = UUID.randomUUID();
    UUID secondMerchant = UUID.randomUUID();
    Money amount = Money.of(10_000, Currency.USD);

    postingService.postPaymentCapture(UUID.randomUUID(), firstMerchant, amount);
    postingService.postPaymentCapture(UUID.randomUUID(), secondMerchant, amount);

    LedgerAccount firstPayable =
        accountService.findMerchantAccount(firstMerchant, "Merchant Payable").orElseThrow();
    LedgerAccount secondPayable =
        accountService.findMerchantAccount(secondMerchant, "Merchant Payable").orElseThrow();

    assertThat(firstPayable.id()).isNotEqualTo(secondPayable.id());
    assertThat(firstPayable.balanceMinor()).isEqualTo(9_710);
    assertThat(secondPayable.balanceMinor()).isEqualTo(9_710);
  }

  @Test
  void reverseCaptureIfExistsUndoesACapturedPaymentsBalances() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    postingService.postPaymentCapture(paymentId, merchantId, amount);

    postingService.reverseCaptureIfExists(paymentId);

    LedgerAccount platformBank = accountService.findPlatformAccount("Platform Bank").orElseThrow();
    LedgerAccount feeRevenue = accountService.findPlatformAccount("Platform Fee Revenue").orElseThrow();
    LedgerAccount merchantPayable =
        accountService.findMerchantAccount(merchantId, "Merchant Payable").orElseThrow();

    assertThat(platformBank.balanceMinor()).isZero();
    assertThat(feeRevenue.balanceMinor()).isZero();
    assertThat(merchantPayable.balanceMinor()).isZero();

    Integer entryCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE transaction_ref = :ref",
            new MapSqlParameterSource("ref", paymentId.toString()),
            Integer.class);
    assertThat(entryCount).isEqualTo(4); // 2 capture legs + 2 reversal legs
  }

  @Test
  void reverseCaptureIfExistsIsANoOpWhenTheLedgerNeverPostedACapture() {
    UUID paymentId = UUID.randomUUID();

    postingService.reverseCaptureIfExists(paymentId);

    Integer entryCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE transaction_ref = :ref",
            new MapSqlParameterSource("ref", paymentId.toString()),
            Integer.class);
    assertThat(entryCount).isZero();
  }
}
