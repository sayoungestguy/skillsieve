-- Resume tailoring suggestions per job (R2, FR-12 part 3). One row per (user, job), replaced on
-- each request. The suggestions are rewritten resume text, so they are encrypted like the resume.
CREATE TABLE tailoring (
  user_id        UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  job_post_id    UUID NOT NULL REFERENCES job_post(id) ON DELETE CASCADE,
  status         TEXT NOT NULL CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
  resume_hmac    CHAR(64) NOT NULL,          -- resume.content_hmac when requested (stale check)
  job_text_hash  CHAR(64) NOT NULL,          -- job_post.text_hash when requested
  ciphertext     BYTEA,                      -- encrypted TailorResult JSON when DONE
  error          TEXT,
  requested_at   TIMESTAMPTZ NOT NULL,
  finished_at    TIMESTAMPTZ,
  PRIMARY KEY (user_id, job_post_id)
);

CREATE INDEX idx_tailoring_job ON tailoring(job_post_id);

ALTER TABLE tailoring ENABLE ROW LEVEL SECURITY;
