package dev.skillsieve.core.messaging;

/** RabbitMQ names shared with skills-svc (TECHNICAL_DESIGN §10). Change both sides together. */
public final class Messaging {

  public static final String EXCHANGE = "skillsieve.events";
  public static final String DEAD_LETTER_EXCHANGE = "skillsieve.dlx";
  public static final String JOB_CREATED = "job.created";
  public static final String EXTRACT_QUEUE = "skills.extract";
  public static final String EXTRACT_DLQ = "skills.extract.dlq";

  /** skills-svc → core-api: {@code job.skills.extracted} and {@code job.skills.failed}. */
  public static final String SKILLS_RESULTS = "job.skills.*";

  public static final String RESULTS_QUEUE = "core.skills-extracted";
  public static final String RESULTS_DLQ = "core.skills-extracted.dlq";
  public static final String SKILLS_EXTRACTED = "job.skills.extracted";
  public static final String SKILLS_FAILED = "job.skills.failed";

  private Messaging() {}
}
