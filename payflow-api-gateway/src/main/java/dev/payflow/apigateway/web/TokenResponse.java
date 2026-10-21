package dev.payflow.apigateway.web;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
