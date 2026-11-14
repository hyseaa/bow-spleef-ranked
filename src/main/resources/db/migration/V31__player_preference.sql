-- The preferences of a player (spec 016, ADR-0023): one row per player who changed a preference, one typed column per
-- preference. No row means the defaults. The first preference is the percentage of the map's TNT already removed when
-- a game starts on the custom server, from 0 to 100, 0 by default.
CREATE TABLE player_preference (
    player_uuid         UUID        PRIMARY KEY REFERENCES player (uuid) ON DELETE CASCADE,
    tnt_removed_percent SMALLINT    NOT NULL DEFAULT 0 CHECK (tnt_removed_percent BETWEEN 0 AND 100),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
