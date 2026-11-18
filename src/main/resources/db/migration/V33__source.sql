-- The sources of matches (spec 033, ADR-0033): the value a client sends in match.source, the name to show for it and
-- its kind: 'bot' (a duel reported in Discord through the bot, wherever it was played) or 'custom_server' (a game sent
-- by a custom server's plugin). match.source has no foreign key to this table: a match from a source that is not
-- registered is still recorded, and is shown as of an unknown source.
CREATE TABLE source (
    name         VARCHAR(50)  PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    kind         VARCHAR(20)  NOT NULL CHECK (kind IN ('bot', 'custom_server'))
);

INSERT INTO source (name, display_name, kind) VALUES
    ('discord_bot', 'Discord bot', 'bot'),
    ('hysea-server', 'Hy Sea''s server', 'custom_server'),
    ('eu-test-server', 'Hy Sea''s EU server', 'custom_server');
