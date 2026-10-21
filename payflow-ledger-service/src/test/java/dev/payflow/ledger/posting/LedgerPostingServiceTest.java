package dev.payflow.ledger.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.ledger.AbstractPostgresIntegrationTest;
import dev.payflow.ledger.account.LedgerAccount;
import dev.payflow.ledger.account.LedgerAccountService;
import dev.payflow.ledger.payout.Payout;
import dev.payflow.ledger.payout.PayoutStatus;
import java.util.List;
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

    // Platform Bank / Platform Fee Revenue are global singletons shared by every test in this
    // class (and every other payment this service ever posts) — asserting their delta rather
    // than an absolute value keeps this test correct regardless of run order or what else in
    // this class also posts a capture.
    long platformBankBefore = accountService.findPlatformAccount("Platform Bank").orElseThrow().balanceMinor();
    long feeRevenueBefore =
        accountService.findPlatformAccount("Platform Fee Revenue").orElseThrow().balanceMinor();

    postingService.postPaymentCapture(paymentId, merchantId, amount);

    LedgerAccount platformBank = accountService.findPlatformAccount("Platform Bank").orElseThrow();
    LedgerAccount feeRevenue =
        accountService.findPlatformAccount("Platform Fee Revenue").orElseThrow();
    LedgerAccount merchantPayable =
        accountService.findMerchantAccount(merchantId, "Merchant Payable").orElseThrow();

    assertThat(feeRevenue.balanceMinor() - feeRevenueBefore).isEqualTo(621);
    assertThat(merchantPayable.balanceMinor()).isEqualTo(20_779);
    assertThat(platformBank.balanceMinor() - platformBankBefore).isEqualTo(21_400);

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

    // Same reasoning as postingACaptureSplitsTheFeeAndUpdatesBothSidesOfBothLegs: Platform Bank
    // / Platform Fee Revenue are shared across this whole test class, so a capture-then-reverse
    // of the SAME amount must leave them at their OWN starting point, not necessarily zero.
    long platformBankBefore = accountService.findPlatformAccount("Platform Bank").orElseThrow().balanceMinor();
    long feeRevenueBefore =
        accountService.findPlatformAccount("Platform Fee Revenue").orElseThrow().balanceMinor();

    postingService.postPaymentCapture(paymentId, merchantId, amount);

    postingService.reverseCaptureIfExists(paymentId);

    LedgerAccount platformBank = accountService.findPlatformAccount("Platform Bank").orElseThrow();
    LedgerAccount feeRevenue = accountService.findPlatformAccount("Platform Fee Revenue").orElseThrow();
    LedgerAccount merchantPayable =
        accountService.findMerchantAccount(merchantId, "Merchant Payable").orElseThrow();

    assertThat(platformBank.balanceMinor()).isEqualTo(platformBankBefore);
    assertThat(feeRevenue.balanceMinor()).isEqualTo(feeRevenueBefore);
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

  @Test
  void createPayoutMovesTheFullMerchantPayableBalanceToPayoutInTransit() {
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    postingService.postPaymentCapture(UUID.randomUUID(), merchantId, amount);

    Payout payout = postingService.createPayout(merchantId);

    LedgerAccount merchantPayable =
        accountService.findMerchantAccount(merchantId, "Merchant Payable").orElseThrow();
    LedgerAccount payoutInTransit =
        accountService.findMerchantAccount(merchantId, "Payout In-Transit").orElseThrow();

    assertThat(merchantPayable.balanceMinor()).isZero();
    assertThat(payoutInTransit.balanceMinor()).isEqualTo(20_779);
    assertThat(payout.merchantId()).isEqualTo(merchantId);
    assertThat(payout.amountMinor()).isEqualTo(20_779);
    assertThat(payout.status()).isEqualTo(PayoutStatus.IN_TRANSIT);
    assertThat(payout.settledAt()).isNull();

    Payout reloaded = postingService.findPayoutById(payout.id()).orElseThrow();
    assertThat(reloaded.merchantId()).isEqualTo(merchantId);
    assertThat(reloaded.amountMinor()).isEqualTo(20_779);
    assertThat(reloaded.status()).isEqualTo(PayoutStatus.IN_TRANSIT);
  }

  @Test
  void createPayoutThrowsWhenTheMerchantHasNoPayableAccountAtAll() {
    UUID merchantId = UUID.randomUUID(); // never captured anything — no account was ever created

    assertThatThrownBy(() -> postingService.createPayout(merchantId))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void createPayoutThrowsWhenTheAvailableBalanceIsZero() {
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    UUID paymentId = UUID.randomUUID();
    postingService.postPaymentCapture(paymentId, merchantId, amount);
    postingService.reverseCaptureIfExists(paymentId); // brings Merchant Payable back to 0

    assertThatThrownBy(() -> postingService.createPayout(merchantId))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void settlePayoutMovesTheBalanceFromPayoutInTransitToPlatformBank() {
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    postingService.postPaymentCapture(UUID.randomUUID(), merchantId, amount);
    Payout payout = postingService.createPayout(merchantId);

    long platformBankBefore = accountService.findPlatformAccount("Platform Bank").orElseThrow().balanceMinor();

    postingService.settlePayout(payout.id());

    LedgerAccount payoutInTransit =
        accountService.findMerchantAccount(merchantId, "Payout In-Transit").orElseThrow();
    LedgerAccount platformBank = accountService.findPlatformAccount("Platform Bank").orElseThrow();

    assertThat(payoutInTransit.balanceMinor()).isZero();
    // Platform Bank is DEBIT-normal (an asset); crediting it on settlement DECREASES its
    // balance — the platform's real cash is actually leaving to pay the merchant's bank, which
    // is exactly what Phase B represents (ARCHITECTURE_AND_PLAN.md §2.3.3: Debit Payout
    // In-Transit, Credit Platform Bank).
    assertThat(platformBank.balanceMinor() - platformBankBefore).isEqualTo(-20_779);

    Payout settled = postingService.findPayoutById(payout.id()).orElseThrow();
    assertThat(settled.status()).isEqualTo(PayoutStatus.SETTLED);
    assertThat(settled.settledAt()).isNotNull();
  }

  @Test
  void settlePayoutIsANoOpWhenAlreadySettled() {
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    postingService.postPaymentCapture(UUID.randomUUID(), merchantId, amount);
    Payout payout = postingService.createPayout(merchantId);
    postingService.settlePayout(payout.id());

    postingService.settlePayout(payout.id()); // replay against an already-settled payout

    Integer entryCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE transaction_ref = :ref",
            new MapSqlParameterSource("ref", "payout-settle-" + payout.id()),
            Integer.class);
    // Phase B is a single leg (one ledger_entries row carries both the debit and credit side of
    // one balanced entry) — unlike capture/refund's fee-split, which posts two. 1, not 2: the
    // replay posted nothing.
    assertThat(entryCount).isEqualTo(1);
  }

  @Test
  void settlePayoutIsANoOpWhenThePayoutDoesNotExist() {
    postingService.settlePayout(UUID.randomUUID()); // must not throw
  }

  @Test
  void sweepAllEligibleMerchantsCreatesAPayoutForEveryMerchantWithAPositiveBalance() {
    UUID firstMerchant = UUID.randomUUID();
    UUID secondMerchant = UUID.randomUUID();
    Money amount = Money.of(10_000, Currency.USD);
    postingService.postPaymentCapture(UUID.randomUUID(), firstMerchant, amount);
    postingService.postPaymentCapture(UUID.randomUUID(), secondMerchant, amount);

    List<Payout> payouts = postingService.sweepAllEligibleMerchants();

    assertThat(payouts)
        .filteredOn(p -> p.merchantId().equals(firstMerchant))
        .singleElement()
        .satisfies(p -> assertThat(p.amountMinor()).isEqualTo(9_710));
    assertThat(payouts)
        .filteredOn(p -> p.merchantId().equals(secondMerchant))
        .singleElement()
        .satisfies(p -> assertThat(p.amountMinor()).isEqualTo(9_710));

    assertThat(accountService.findMerchantAccount(firstMerchant, "Merchant Payable").orElseThrow().balanceMinor())
        .isZero();
    assertThat(accountService.findMerchantAccount(secondMerchant, "Merchant Payable").orElseThrow().balanceMinor())
        .isZero();
  }

  @Test
  void sweepAllEligibleMerchantsExcludesAMerchantWithNoAvailableBalance() {
    UUID merchantId = UUID.randomUUID();
    UUID paymentId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    postingService.postPaymentCapture(paymentId, merchantId, amount);
    postingService.reverseCaptureIfExists(paymentId); // balance back to 0

    List<Payout> payouts = postingService.sweepAllEligibleMerchants();

    assertThat(payouts).noneMatch(p -> p.merchantId().equals(merchantId));
  }

  @Test
  void sumTotalDebitsAndSumTotalCreditsStayEqualAfterRealPostings() {
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    postingService.postPaymentCapture(UUID.randomUUID(), merchantId, amount);

    assertThat(postingService.sumTotalDebits()).isEqualTo(postingService.sumTotalCredits());
  }
}
