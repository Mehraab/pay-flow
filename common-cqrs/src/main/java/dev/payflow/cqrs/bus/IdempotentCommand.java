package dev.payflow.cqrs.bus;

/**
 * Opt-in for a {@link Command} that must be deduplicated by a caller-supplied key (BR-2 — never
 * charge the same payment twice on a retried request). {@code resultType()} lets a generic
 * interceptor deserialize a cached result back into {@code R} despite type erasure, without
 * hardcoding knowledge of any specific command.
 */
public interface IdempotentCommand<R> extends Command<R> {

  String idempotencyKey();

  Class<R> resultType();
}
