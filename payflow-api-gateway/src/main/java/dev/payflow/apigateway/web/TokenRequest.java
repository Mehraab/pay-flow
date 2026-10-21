package dev.payflow.apigateway.web;

import java.util.UUID;

public record TokenRequest(String role, UUID merchantId) {}
