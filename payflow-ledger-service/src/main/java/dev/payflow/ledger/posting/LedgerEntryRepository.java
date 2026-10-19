package dev.payflow.ledger.posting;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerEntryRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public LedgerEntryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean existsForTransactionRef(String transactionRef) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE transaction_ref = :ref",
            new MapSqlParameterSource("ref", transactionRef),
            Integer.class);
    return count != null && count > 0;
  }

  public void insertEntry(
      String transactionRef, UUID debitAccountId, UUID creditAccountId, long amountMinor) {
    jdbc.update(
        """
        INSERT INTO ledger_entries (transaction_ref, debit_account_id, credit_account_id, amount_minor)
        VALUES (:ref, :debitAccountId, :creditAccountId, :amountMinor)
        """,
        new MapSqlParameterSource()
            .addValue("ref", transactionRef)
            .addValue("debitAccountId", debitAccountId)
            .addValue("creditAccountId", creditAccountId)
            .addValue("amountMinor", amountMinor));
  }
}
