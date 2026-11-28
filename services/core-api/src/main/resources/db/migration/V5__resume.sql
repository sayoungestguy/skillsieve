-- One resume per user (R2, FR-12). The text is encrypted by core-api (AES-256-GCM, key in the
-- RESUME_KEY Secret), so a database dump alone does not reveal it. content_hmac is an HMAC of the
-- plain text, used later to cache tailoring suggestions without storing the text twice.
CREATE TABLE resume (
  user_id      UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  ciphertext   BYTEA NOT NULL,               -- version (1 byte) | nonce (12) | ciphertext + tag
  content_hmac CHAR(64) NOT NULL,
  char_count   INTEGER NOT NULL,
  updated_at   TIMESTAMPTZ NOT NULL
);

ALTER TABLE resume ENABLE ROW LEVEL SECURITY;
