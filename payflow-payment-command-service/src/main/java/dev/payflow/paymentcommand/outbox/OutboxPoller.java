package dev.payflow.paymentcommand.outbox;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes {@code outbox_events} rows to Kafka and marks them published — the ONLY component in
 * this service that imports {@link KafkaTemplate} (AGENT_AND_DEV_GUIDELINES.md, Rule 3). No
 * controller or handler ever publishes directly; the outbox table is the sole path to Kafka.
 *
 * <p>{@code send(...).get()} blocks until Kafka actually acknowledges the publish before this
 * method's transaction commits {@code published_at} — a Kafka outage rolls the whole batch back,
 * so the row stays unpublished and the next poll retries it, rather than marking it published on
 * a send that may not have actually happened.
 *
 * <p>The event type travels as the {@code event-type} Kafka header, not inside the JSON payload
 * itself — {@code PaymentCaptured}/{@code PaymentFailed} don't repeat the payment id or any
 * other discriminating field in their own records (see {@code PaymentDomainEvent}'s
 * subtypes), and a Jackson type-discriminator annotation on that sealed interface would violate
 * Rule 1 (Domain Purity — no Jackson imports under {@code ..domain..}). A header is the only
 * place left for a consumer to learn which of the three payload shapes it's looking at.
 */
@Component
public class OutboxPoller {

  private static final String TOPIC = "payment.events";
  private static final String EVENT_TYPE_HEADER = "event-type";
  private static final int BATCH_SIZE = 50;

  private final NamedParameterJdbcTemplate jdbc;
  // Spring Boot's autoconfigured KafkaTemplate bean is declared KafkaTemplate<Object, Object> —
  // that's fixed by KafkaAutoConfiguration itself, independent of the configured serializers —
  // so a more specific <String, String> constructor parameter can never match it by generic-type
  // autowiring. Runtime (de)serialization is still String-typed, via spring.kafka.producer.*
  // serializer properties.
  private final KafkaTemplate<Object, Object> kafkaTemplate;

  public OutboxPoller(NamedParameterJdbcTemplate jdbc, KafkaTemplate<Object, Object> kafkaTemplate) {
    this.jdbc = jdbc;
    this.kafkaTemplate = kafkaTemplate;
  }

  @Scheduled(fixedDelay = 200)
  @Transactional
  public void publishUnpublishedEvents() {
    List<OutboxRow> rows =
        jdbc.query(
            """
            SELECT id, aggregate_id, event_type, payload
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """,
            new MapSqlParameterSource("batchSize", BATCH_SIZE),
            (rs, rowNum) ->
                new OutboxRow(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("aggregate_id")),
                    rs.getString("event_type"),
                    rs.getString("payload")));

    for (OutboxRow row : rows) {
      publish(row);
      markPublished(row.id());
    }
  }

  private void publish(OutboxRow row) {
    ProducerRecord<Object, Object> record =
        new ProducerRecord<>(TOPIC, row.aggregateId().toString(), row.payload());
    record
        .headers()
        .add(new RecordHeader(EVENT_TYPE_HEADER, row.eventType().getBytes(StandardCharsets.UTF_8)));
    try {
      kafkaTemplate.send(record).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while publishing outbox event " + row.id(), e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to publish outbox event " + row.id(), e.getCause());
    }
  }

  private void markPublished(UUID id) {
    jdbc.update(
        "UPDATE outbox_events SET published_at = :now WHERE id = :id",
        new MapSqlParameterSource()
            .addValue("now", Timestamp.from(Instant.now()))
            .addValue("id", id));
  }

  private record OutboxRow(UUID id, UUID aggregateId, String eventType, String payload) {}
}
