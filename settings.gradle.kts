rootProject.name = "payflow"

include(
    "common-cqrs",
    "payflow-common",
    "payflow-api-gateway",
    "payflow-payment-command-service",
    "payflow-payment-query-service",
    "payflow-provider-gateway-service",
    "payflow-payment-orchestrator",
    "payflow-ledger-service",
    "payflow-notification-service",
)
