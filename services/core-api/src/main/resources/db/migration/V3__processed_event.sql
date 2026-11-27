-- Inbox for RabbitMQ consumers (R2): delivery is at-least-once, so each eventId is applied once.
CREATE TABLE processed_event (
  event_id     UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE processed_event ENABLE ROW LEVEL SECURITY;
