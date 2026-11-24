-- SkillSieve schema v1 (docs/TECHNICAL_DESIGN.md §3). Flyway runs this inside schema `skillsieve`.

CREATE TABLE app_user (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'OWNER' CHECK (role IN ('OWNER','DEMO','USER')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE job_post (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  company           TEXT,
  title             TEXT,
  location          TEXT,
  source_url        TEXT,
  raw_text          TEXT NOT NULL,
  text_hash         CHAR(64) NOT NULL,
  seniority         TEXT,
  min_years         SMALLINT,
  extraction_status TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (extraction_status IN ('PENDING','DONE','FAILED')),
  extraction_error  TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, text_hash)
);

CREATE TABLE application (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_post_id     UUID NOT NULL UNIQUE REFERENCES job_post(id) ON DELETE CASCADE,
  status          TEXT NOT NULL DEFAULT 'SAVED'
                  CHECK (status IN ('SAVED','APPLIED','INTERVIEW','OFFER','REJECTED','WITHDRAWN')),
  applied_at      TIMESTAMPTZ,
  next_action_at  DATE,
  notes           TEXT,
  position        INT NOT NULL DEFAULT 0,
  version         INT NOT NULL DEFAULT 0,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE status_event (
  id             BIGSERIAL PRIMARY KEY,
  application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
  from_status    TEXT,
  to_status      TEXT NOT NULL,
  changed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE skill (
  id        SERIAL PRIMARY KEY,
  slug      TEXT NOT NULL UNIQUE,
  name      TEXT NOT NULL,
  category  TEXT NOT NULL
            CHECK (category IN ('LANGUAGE','FRAMEWORK','DATABASE','CLOUD','DEVOPS','AI','PRACTICE','SOFT','OTHER'))
);

CREATE TABLE skill_alias (
  alias     TEXT PRIMARY KEY,
  skill_id  INT NOT NULL REFERENCES skill(id) ON DELETE CASCADE
);

CREATE TABLE job_skill (
  job_post_id UUID NOT NULL REFERENCES job_post(id) ON DELETE CASCADE,
  skill_id    INT  NOT NULL REFERENCES skill(id) ON DELETE RESTRICT,
  importance  TEXT NOT NULL CHECK (importance IN ('REQUIRED','PREFERRED')),
  evidence    TEXT,
  PRIMARY KEY (job_post_id, skill_id)
);

CREATE TABLE user_skill (
  user_id    UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  skill_id   INT  NOT NULL REFERENCES skill(id) ON DELETE RESTRICT,
  level      SMALLINT NOT NULL CHECK (level BETWEEN 0 AND 3),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, skill_id)
);

CREATE TABLE extraction_cache (
  text_hash      CHAR(64) PRIMARY KEY,
  model          TEXT NOT NULL,
  prompt_version TEXT NOT NULL,
  result_json    JSONB NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_post_user_created ON job_post(user_id, created_at DESC);
CREATE INDEX idx_application_status    ON application(status);
CREATE INDEX idx_status_event_app      ON status_event(application_id, changed_at);
CREATE INDEX idx_job_skill_skill       ON job_skill(skill_id);
CREATE INDEX idx_user_skill_skill      ON user_skill(skill_id);
CREATE INDEX idx_skill_alias_skill     ON skill_alias(skill_id);

-- Row Level Security: on for every table, with no policies. The owning role (skillsieve_api, which
-- runs these migrations) bypasses RLS; every other role, including Supabase's anon and
-- authenticated, sees nothing. Per-user ownership is enforced in the service layer.
ALTER TABLE app_user         ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_post         ENABLE ROW LEVEL SECURITY;
ALTER TABLE application      ENABLE ROW LEVEL SECURITY;
ALTER TABLE status_event     ENABLE ROW LEVEL SECURITY;
ALTER TABLE skill            ENABLE ROW LEVEL SECURITY;
ALTER TABLE skill_alias      ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_skill        ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_skill       ENABLE ROW LEVEL SECURITY;
ALTER TABLE extraction_cache ENABLE ROW LEVEL SECURITY;
