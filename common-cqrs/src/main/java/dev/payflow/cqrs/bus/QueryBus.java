package dev.payflow.cqrs.bus;

public interface QueryBus {

  <R> R dispatch(Query<R> query);
}
