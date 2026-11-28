package dev.skillsieve.core.token;

/** Returned once on create: {@code token} is the only time the plain value leaves the server. */
public record CreatedTokenDto(AccessTokenDto info, String token) {}
