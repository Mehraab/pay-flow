package dev.payflow.ledger.consumer;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consumer-side dedup for at-least-once Kafka delivery (ARCHITECTURE_AND_PLAN.md §2.2 / BR-12):
 * {@code (event_id, consumer_name)} is the table's primary key, so a replayed event's insert
 * affects zero rows.
 */
@Repository
public class ProcessedEventRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public ProcessedEventRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Returns {@code true} if this is the first time {@code eventId} was seen by {@code consumerName}. */
  public boolean markProcessed(UUID eventId, String consumerName) {
    int rows =
        jdbc.update(
            """
            INSERT INTO processed_events (event_id, consumer_name)
            VALUES (:eventId, :consumerName)
            ON CONFLICT DO NOTHING
            """,
            new MapSqlParameterSource().addValue("eventId", eventId).addValue("consumerName", consumerName));
    return rows > 0;
  }
}
