package dev.skillsieve.core.insight;

public record InsightSummaryDto(
    long total, long applied, long interviews, long offers, double interviewRate) {}
