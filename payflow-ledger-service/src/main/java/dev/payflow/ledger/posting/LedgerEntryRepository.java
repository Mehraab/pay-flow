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
