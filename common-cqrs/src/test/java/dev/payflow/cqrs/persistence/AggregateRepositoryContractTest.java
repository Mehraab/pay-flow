package dev.payflow.cqrs.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.payflow.cqrs.domain.TestCounterAggregate;
import dev.payflow.cqrs.exception.AggregateNotFoundException;
import dev.payflow.cqrs.exception.ConcurrencyException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregateRepositoryContractTest {

  private final InMemoryAggregateRepository<
          TestCounterAggregate, UUID, TestCounterAggregate.CounterEvent>
      repository = new InMemoryAggregateRepository<>();

  @Test
  void savingAFreshAggregatePersistsItAndAdvancesItsVersion() {
    var id = UUID.randomUUID();
    var counter = new TestCounterAggregate(id, 0);
    counter.increment(1);

    repository.save(counter);

    assertThat(repository.findById(id)).isPresent();
    assertThat(repository.findById(id).orElseThrow().version()).isEqualTo(1);
  }

  @Test
  void savingAtAStaleVersionThrowsConcurrencyException() {
    var id = UUID.randomUUID();
    repository.save(new TestCounterAggregate(id, 0)); // now stored at version 1

    var staleCopy = new TestCounterAggregate(id, 0); // still thinks it's at version 0
    staleCopy.increment(1);

    assertThatThrownBy(() -> repository.save(staleCopy)).isInstanceOf(ConcurrencyException.class);
  }

  @Test
  void getByIdThrowsAggregateNotFoundExceptionWhenMissing() {
    assertThatThrownBy(() -> repository.getById(UUID.randomUUID()))
        .isInstanceOf(AggregateNotFoundException.class);
  }
}
