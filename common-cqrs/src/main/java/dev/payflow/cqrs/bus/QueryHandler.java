package dev.payflow.cqrs.bus;

public interface QueryHandler<Q extends Query<R>, R> {

  R handle(Q query);
}
