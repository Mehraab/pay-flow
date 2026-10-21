package dev.payflow.ledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.ledger.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LedgerAccountSlugLookupTest extends AbstractPostgresIntegrationTest {

  @Autowired private LedgerAccountService accountService;

  @Test
  void platformAccountIsFoundByKebabCaseSlug() {
    LedgerAccount bySlug = accountService.findPlatformAccount("platform-bank").orElseThrow();
    assertThat(bySlug.accountName()).isEqualTo("Platform Bank");
  }

  @Test
  void platformAccountIsStillFoundByItsExactDisplayName() {
    LedgerAccount byExactName =
        accountService.findPlatformAccount("Platform Fee Revenue").orElseThrow();
    assertThat(byExactName.accountName()).isEqualTo("Platform Fee Revenue");
  }

  @Test
  void merchantAccountIsFoundByKebabCaseSlugWhenMerchantIdIsKnown() {
    UUID merchantId = UUID.randomUUID();
    accountService.ensureMerchantPayableAccount(merchantId);

    LedgerAccount bySlug =
        accountService.findMerchantAccount(merchantId, "merchant-payable").orElseThrow();
    assertThat(bySlug.accountName()).isEqualTo("Merchant Payable");
  }

  @Test
  void uniqueMerchantLookupReturnsEmptyWhenMultipleMerchantsShareTheAccountName() {
    UUID firstMerchant = UUID.randomUUID();
    UUID secondMerchant = UUID.randomUUID();
    accountService.ensureMerchantPayableAccount(firstMerchant);
    accountService.ensureMerchantPayableAccount(secondMerchant);

    assertThat(accountService.findUniqueMerchantAccountByNameOrSlug("merchant-payable"))
        .isEmpty();
  }

  @Test
  void uniqueMerchantLookupReturnsEmptyWhenNoMerchantHasTheAccountName() {
    assertThat(accountService.findUniqueMerchantAccountByNameOrSlug("nonexistent-account"))
        .isEmpty();
  }
}
