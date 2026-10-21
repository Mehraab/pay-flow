package dev.payflow.apigateway.security;

import java.util.UUID;

/** What a refresh token resolves back to — enough to reissue an equivalent access token. */
public record RefreshTokenData(String role, UUID merchantId) {}
