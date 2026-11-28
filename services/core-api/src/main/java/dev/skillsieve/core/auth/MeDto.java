package dev.skillsieve.core.auth;

import java.util.UUID;

/**
 * The signed-in user. {@code viaKeycloak}: this session came from Keycloak sign-in, so signing out
 * should also end the Keycloak session (ADR-009).
 */
public record MeDto(UUID id, String email, Role role, boolean viaKeycloak) {}
