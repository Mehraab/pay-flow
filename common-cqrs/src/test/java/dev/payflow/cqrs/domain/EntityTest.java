package dev.payflow.cqrs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityTest {

  @Test
  void twoEntitiesOfTheSameTypeWithTheSameIdAreEqual() {
    var id = UUID.randomUUID();
    var a = new TestLineItemEntity(id);
    var b = new TestLineItemEntity(id);

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
  }

  @Test
  void entitiesWithDifferentIdsAreNeverEqual() {
    var a = new TestLineItemEntity(UUID.randomUUID());
    var b = new TestLineItemEntity(UUID.randomUUID());

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void entitiesOfDifferentTypesAreNeverEqualEvenWithTheSameId() {
    var id = UUID.randomUUID();
    var lineItem = new TestLineItemEntity(id);
    var other = new AnotherTestEntity(id);

    assertThat(lineItem).isNotEqualTo(other);
    assertThat(other).isNotEqualTo(lineItem);
  }
}
