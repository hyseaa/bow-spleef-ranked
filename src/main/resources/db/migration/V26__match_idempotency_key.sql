-- Optional client-supplied key that makes POST /matches safe to retry (spec 002).
-- Existing matches keep a null key; the key is unique per source.
ALTER TABLE match ADD COLUMN idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX uq_match_source_idempotency_key
    ON match (source, idempotency_key) WHERE idempotency_key IS NOT NULL;
