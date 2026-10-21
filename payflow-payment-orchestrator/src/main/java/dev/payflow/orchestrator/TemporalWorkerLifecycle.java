package dev.payflow.orchestrator;

import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Starts the Temporal worker once the application context is fully up, stops it on shutdown. */
@Component
public class TemporalWorkerLifecycle {

  private static final Logger log = LoggerFactory.getLogger(TemporalWorkerLifecycle.class);

  private final WorkerFactory workerFactory;

  public TemporalWorkerLifecycle(WorkerFactory workerFactory) {
    this.workerFactory = workerFactory;
  }

  /**
   * {@code WorkerFactory.start()} connects synchronously — if the Temporal server isn't
   * reachable yet (a startup-order race across independently-started services, or simply not
   * running in this environment), that must not take the rest of this application down with it.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void startWorker() {
    try {
      workerFactory.start();
    } catch (RuntimeException e) {
      log.warn("Could not start the Temporal worker — is the Temporal server running? {}", e.getMessage());
    }
  }

  @PreDestroy
  public void stopWorker() {
    try {
      workerFactory.shutdown();
    } catch (RuntimeException e) {
      log.warn("Error shutting down the Temporal worker: {}", e.getMessage());
    }
  }
}
