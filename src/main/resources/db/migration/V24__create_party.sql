CREATE TABLE party (
    id          BIGSERIAL   PRIMARY KEY,
    leader_uuid UUID        NOT NULL REFERENCES player(uuid),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- The global UNIQUE on player_uuid enforces "one party per player";
-- the leader also has a member row, so it prevents leading two parties too.
CREATE TABLE party_member (
    id          BIGSERIAL   PRIMARY KEY,
    party_id    BIGINT      NOT NULL REFERENCES party(id) ON DELETE CASCADE,
    player_uuid UUID        NOT NULL UNIQUE REFERENCES player(uuid),
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_party_member_party_id ON party_member(party_id);

CREATE TABLE party_invite (
    id          BIGSERIAL   PRIMARY KEY,
    party_id    BIGINT      NOT NULL REFERENCES party(id) ON DELETE CASCADE,
    player_uuid UUID        NOT NULL REFERENCES player(uuid),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_party_invite UNIQUE (party_id, player_uuid)
);

CREATE INDEX idx_party_invite_party_id ON party_invite(party_id);
CREATE INDEX idx_party_invite_player_uuid ON party_invite(player_uuid);
CREATE INDEX idx_party_invite_expires_at ON party_invite(expires_at);
