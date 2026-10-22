package dev.payflow.ledger.payout;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayoutRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public PayoutRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(Payout payout) {
    jdbc.update(
        """
        INSERT INTO payouts (id, merchant_id, amount_minor, currency, status, created_at, settled_at)
        VALUES (:id, :merchantId, :amountMinor, :currency, :status, :createdAt, :settledAt)
        """,
        paramsFor(payout));
  }

  public Optional<Payout> findById(UUID id) {
    List<Payout> rows =
        jdbc.query("SELECT * FROM payouts WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
    return rows.stream().findFirst();
  }

  public void updateStatus(UUID id, PayoutStatus status, Instant settledAt) {
    jdbc.update(
        "UPDATE payouts SET status = :status, settled_at = :settledAt WHERE id = :id",
        new MapSqlParameterSource()
            .addValue("status", status.name())
            .addValue("settledAt", settledAt != null ? Timestamp.from(settledAt) : null)
            .addValue("id", id));
  }

  /** Payouts still awaiting Phase B settlement (Milestone 3 Chunk 2). */
  public List<Payout> findPendingSettlement() {
    return jdbc.query(
        "SELECT * FROM payouts WHERE status = 'IN_TRANSIT' ORDER BY created_at ASC", this::mapRow);
  }

  /** BE-10: merchant payout history, newest first. */
  public List<Payout> findByMerchantId(UUID merchantId, int offset, int limit) {
    return jdbc.query(
        """
        SELECT * FROM payouts
        WHERE merchant_id = :merchantId
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """,
        new MapSqlParameterSource().addValue("merchantId", merchantId).addValue("limit", limit).addValue("offset", offset),
        this::mapRow);
  }

  public long countByMerchantId(UUID merchantId) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM payouts WHERE merchant_id = :merchantId",
            new MapSqlParameterSource().addValue("merchantId", merchantId),
            Long.class);
    return count != null ? count : 0L;
  }

  private MapSqlParameterSource paramsFor(Payout payout) {
    return new MapSqlParameterSource()
        .addValue("id", payout.id())
        .addValue("merchantId", payout.merchantId())
        .addValue("amountMinor", payout.amountMinor())
        .addValue("currency", payout.currency())
        .addValue("status", payout.status().name())
        .addValue("createdAt", Timestamp.from(payout.createdAt()))
        .addValue("settledAt", payout.settledAt() != null ? Timestamp.from(payout.settledAt()) : null);
  }

  private Payout mapRow(ResultSet rs, int rowNum) throws SQLException {
    Timestamp settledAt = rs.getTimestamp("settled_at");
    return new Payout(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("merchant_id")),
        rs.getLong("amount_minor"),
        rs.getString("currency"),
        PayoutStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant(),
        settledAt != null ? settledAt.toInstant() : null);
  }
}
