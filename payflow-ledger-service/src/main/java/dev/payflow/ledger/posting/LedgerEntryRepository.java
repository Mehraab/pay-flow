package dev.payflow.ledger.posting;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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

  public List<LedgerEntry> findByTransactionRef(String transactionRef) {
    return jdbc.query(
        """
        SELECT id, transaction_ref, debit_account_id, credit_account_id, amount_minor, posted_at
        FROM ledger_entries
        WHERE transaction_ref = :ref
        ORDER BY posted_at ASC
        """,
        new MapSqlParameterSource("ref", transactionRef),
        this::mapRow);
  }

  /**
   * BR-12 ledger integrity (§3): the cross-row aggregate a SQL CHECK constraint can't express —
   * see {@code GET /operator/ledger/integrity}. Note that for THIS schema, where every single
   * row already carries one debit leg and one credit leg of the identical {@code amount_minor},
   * this sum is mathematically always equal to {@link #sumTotalCredits()} — the real value of
   * this pair of queries is as a standing, re-runnable assertion of that invariant, not as a
   * check capable of catching an unbalanced row that the schema itself cannot represent anyway.
   */
  public long sumTotalDebits() {
    Long sum = jdbc.queryForObject("SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries", new MapSqlParameterSource(), Long.class);
    return sum != null ? sum : 0L;
  }

  public long sumTotalCredits() {
    Long sum = jdbc.queryForObject("SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries", new MapSqlParameterSource(), Long.class);
    return sum != null ? sum : 0L;
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

  private LedgerEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new LedgerEntry(
        UUID.fromString(rs.getString("id")),
        rs.getString("transaction_ref"),
        UUID.fromString(rs.getString("debit_account_id")),
        UUID.fromString(rs.getString("credit_account_id")),
        rs.getLong("amount_minor"),
        rs.getTimestamp("posted_at").toInstant());
  }
}
