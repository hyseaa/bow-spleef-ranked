ALTER TABLE duel_challenge
    DROP CONSTRAINT duel_challenge_match_id_fkey,
    ADD CONSTRAINT duel_challenge_match_id_fkey
        FOREIGN KEY (match_id) REFERENCES match(id) ON DELETE SET NULL;
