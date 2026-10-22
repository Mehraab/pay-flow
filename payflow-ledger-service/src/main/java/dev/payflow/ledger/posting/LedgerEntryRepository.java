package dev.payflow.ledger.posting;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
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

  /**
   * BE-06: platform-wide ledger feed, newest first. {@code since == null} means no lower bound.
   * The explicit {@code CAST(... AS timestamptz)} is required, not decorative: under Postgres's
   * extended query protocol, a bare {@code $1 IS NULL} gives the parser no type to infer for that
   * placeholder (every type satisfies {@code IS NULL}) and it fails with "could not determine
   * data type of parameter $1" — the JDBC-side {@code Types.TIMESTAMP} hint on the bind value
   * doesn't reach Postgres's parse step, which runs before any value is bound.
   */
  public List<LedgerEntry> findEntries(Instant since, int offset, int limit) {
    return jdbc.query(
        """
        SELECT id, transaction_ref, debit_account_id, credit_account_id, amount_minor, posted_at
        FROM ledger_entries
        WHERE (CAST(:since AS timestamptz) IS NULL OR posted_at >= CAST(:since AS timestamptz))
        ORDER BY posted_at DESC
        LIMIT :limit OFFSET :offset
        """,
        new MapSqlParameterSource()
            .addValue("since", since != null ? Timestamp.from(since) : null, Types.TIMESTAMP)
            .addValue("limit", limit)
            .addValue("offset", offset),
        this::mapRow);
  }

  public long countEntries(Instant since) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM ledger_entries
            WHERE (CAST(:since AS timestamptz) IS NULL OR posted_at >= CAST(:since AS timestamptz))
            """,
            new MapSqlParameterSource()
                .addValue("since", since != null ? Timestamp.from(since) : null, Types.TIMESTAMP),
            Long.class);
    return count != null ? count : 0L;
  }

  /**
   * BE-09: merchant-wide ledger feed. An entry belongs to a merchant if either leg of its
   * double-entry pair posts to one of that merchant's accounts — an {@code EXISTS} subquery
   * rather than a join + {@code DISTINCT}, since a join on {@code debit_account_id OR
   * credit_account_id} would otherwise risk returning the same row twice.
   */
  public List<LedgerEntry> findEntriesByMerchantId(UUID merchantId, int offset, int limit) {
    return jdbc.query(
        """
        SELECT e.id, e.transaction_ref, e.debit_account_id, e.credit_account_id, e.amount_minor, e.posted_at
        FROM ledger_entries e
        WHERE EXISTS (
            SELECT 1 FROM ledger_accounts a
            WHERE a.merchant_id = :merchantId
              AND (a.id = e.debit_account_id OR a.id = e.credit_account_id)
        )
        ORDER BY e.posted_at DESC
        LIMIT :limit OFFSET :offset
        """,
        new MapSqlParameterSource().addValue("merchantId", merchantId).addValue("limit", limit).addValue("offset", offset),
        this::mapRow);
  }

  public long countEntriesByMerchantId(UUID merchantId) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM ledger_entries e
            WHERE EXISTS (
                SELECT 1 FROM ledger_accounts a
                WHERE a.merchant_id = :merchantId
                  AND (a.id = e.debit_account_id OR a.id = e.credit_account_id)
            )
            """,
            new MapSqlParameterSource().addValue("merchantId", merchantId),
            Long.class);
    return count != null ? count : 0L;
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
