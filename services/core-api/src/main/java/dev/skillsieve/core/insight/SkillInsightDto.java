package dev.skillsieve.core.insight;

public record SkillInsightDto(
    String slug,
    String name,
    String category,
    int requiredPct,
    int preferredPct,
    int totalPct,
    int myLevel,
    boolean isGap) {}
