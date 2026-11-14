-- The modifiers a game was played with (spec 010, ADR-0021): a JSON object such as {"no-collision": true, "perks": 20},
-- sent by the custom server. Null for every match that carries no such information (existing matches, the bot's).
ALTER TABLE match ADD COLUMN modifiers JSONB;
