package dev.payflow.notification.delivery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookEndpointRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public WebhookEndpointRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<WebhookEndpoint> findActiveByMerchantId(UUID merchantId) {
    List<WebhookEndpoint> rows =
        jdbc.query(
            """
            SELECT id, merchant_id, url, signing_secret, active
            FROM webhook_endpoints
            WHERE merchant_id = :merchantId AND active = true
            LIMIT 1
            """,
            new MapSqlParameterSource("merchantId", merchantId),
            this::mapRow);
    return rows.stream().findFirst();
  }

  private WebhookEndpoint mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new WebhookEndpoint(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("merchant_id")),
        rs.getString("url"),
        rs.getString("signing_secret"),
        rs.getBoolean("active"));
  }
}
