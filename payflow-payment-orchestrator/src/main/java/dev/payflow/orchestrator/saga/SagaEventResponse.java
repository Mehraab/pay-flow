package dev.payflow.orchestrator.saga;

import java.time.Instant;

/** One row of the saga audit log, read straight from Temporal's own Visibility API (BR-14). */
public record SagaEventResponse(String workflowId, String status, Instant startTime, Instant closeTime) {}
