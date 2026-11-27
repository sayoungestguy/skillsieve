-- Transactional outbox (R2, TECHNICAL_DESIGN §10): an event row commits with the change that caused
-- it; OutboxPublisher sends it to RabbitMQ and marks it sent, so a broker outage loses nothing.
CREATE TABLE outbox (
  id           UUID PRIMARY KEY,                 -- also the message eventId (idempotency key)
  event_type   TEXT NOT NULL,                    -- RabbitMQ routing key, e.g. job.created
  aggregate_id UUID NOT NULL,                    -- job_post.id
  payload      JSONB NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL,
  sent_at      TIMESTAMPTZ,
  attempts     INT NOT NULL DEFAULT 0,
  last_error   TEXT
);

CREATE INDEX idx_outbox_unsent ON outbox(created_at) WHERE sent_at IS NULL;

ALTER TABLE outbox ENABLE ROW LEVEL SECURITY;
