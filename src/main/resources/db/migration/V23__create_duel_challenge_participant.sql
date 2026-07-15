CREATE TABLE duel_challenge_participant (
    id           BIGSERIAL   PRIMARY KEY,
    challenge_id BIGINT      NOT NULL REFERENCES duel_challenge(id) ON DELETE CASCADE,
    player_uuid  UUID        NOT NULL REFERENCES player(uuid),
    side         VARCHAR(10) NOT NULL CHECK (side IN ('CHALLENGER', 'CHALLENGED')),
    CONSTRAINT uq_duel_challenge_participant UNIQUE (challenge_id, player_uuid)
);

CREATE INDEX idx_dcp_challenge_id ON duel_challenge_participant(challenge_id);

-- Backfill: captains of existing challenges become participants
INSERT INTO duel_challenge_participant (challenge_id, player_uuid, side)
SELECT id, challenger_uuid, 'CHALLENGER' FROM duel_challenge
ON CONFLICT DO NOTHING;

INSERT INTO duel_challenge_participant (challenge_id, player_uuid, side)
SELECT id, challenged_uuid, 'CHALLENGED' FROM duel_challenge
ON CONFLICT DO NOTHING;
