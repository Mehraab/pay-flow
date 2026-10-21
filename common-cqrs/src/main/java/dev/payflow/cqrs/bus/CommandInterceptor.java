package dev.payflow.cqrs.bus;

/**
 * Chain-of-responsibility hook around every command's execution — idempotency-key validation,
 * auth, metrics, and logging all plug in here (AGENT_AND_DEV_GUIDELINES.md, Rule 4) rather than
 * being duplicated inside individual handlers.
 */
public interface CommandInterceptor {

  <C extends Command<R>, R> R intercept(C command, CommandInvocation<C, R> next);
}
