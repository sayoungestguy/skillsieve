-- Personal access tokens for the Chrome extension (R2, TECHNICAL_DESIGN §14). Only a SHA-256 of
-- the token is stored; the plain value is shown once when created.
CREATE TABLE access_token (
  id           UUID PRIMARY KEY,
  user_id      UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  token_hash   CHAR(64) NOT NULL UNIQUE,
  prefix       TEXT NOT NULL,                  -- first characters, to tell tokens apart in the UI
  created_at   TIMESTAMPTZ NOT NULL,
  last_used_at TIMESTAMPTZ,
  revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_access_token_user ON access_token(user_id);

ALTER TABLE access_token ENABLE ROW LEVEL SECURITY;
