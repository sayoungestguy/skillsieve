package dev.skillsieve.core.auth;

import java.util.UUID;

public record MeDto(UUID id, String email, Role role) {}
