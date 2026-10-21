package dev.payflow.cqrs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

  @Test
  void raisingAnEventMutatesStateImmediately() {
    var counter = new TestCounterAggregate(UUID.randomUUID(), 0);

    counter.increment(5);

    assertThat(counter.value()).isEqualTo(5);
  }

  @Test
  void raisingSeveralEventsAccumulatesState() {
    var counter = new TestCounterAggregate(UUID.randomUUID(), 0);

    counter.increment(2);
    counter.increment(3);

    assertThat(counter.value()).isEqualTo(5);
  }

  @Test
  void pullUncommittedEventsDrainsAndClearsTheBuffer() {
    var counter = new TestCounterAggregate(UUID.randomUUID(), 0);
    counter.increment(2);
    counter.increment(3);

    var events = counter.pullUncommittedEvents();

    assertThat(events).hasSize(2);
    assertThat(counter.pullUncommittedEvents()).isEmpty();
  }

  @Test
  void eventsCarryTheAggregateIdAndCorrelationMetadata() {
    var id = UUID.randomUUID();
    var counter = new TestCounterAggregate(id, 0);

    counter.increment(1);

    var event = counter.pullUncommittedEvents().get(0);
    assertThat(event.aggregateId()).isEqualTo(id);
    assertThat(event.eventId()).isNotNull();
    assertThat(event.occurredOn()).isNotNull();
    assertThat(event.correlationId()).isNotNull();
    assertThat(event.causationId()).isNull();
  }

  @Test
  void incrementVersionAdvancesTheOptimisticLockVersion() {
    var counter = new TestCounterAggregate(UUID.randomUUID(), 0);

    counter.incrementVersion();

    assertThat(counter.version()).isEqualTo(1);
  }

  @Test
  void twoAggregatesWithTheSameIdAreEqualRegardlessOfState() {
    var id = UUID.randomUUID();
    var a = new TestCounterAggregate(id, 0);
    var b = new TestCounterAggregate(id, 7);
    b.increment(99);

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
  }

  @Test
  void aggregatesWithDifferentIdsAreNeverEqual() {
    var a = new TestCounterAggregate(UUID.randomUUID(), 0);
    var b = new TestCounterAggregate(UUID.randomUUID(), 0);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void aggregatesOfDifferentTypesAreNeverEqualEvenWithTheSameId() {
    var id = UUID.randomUUID();
    var counter = new TestCounterAggregate(id, 0);
    var other = new AnotherTestAggregate(id, 0);

    assertThat(counter).isNotEqualTo(other);
    assertThat(other).isNotEqualTo(counter);
  }
}
