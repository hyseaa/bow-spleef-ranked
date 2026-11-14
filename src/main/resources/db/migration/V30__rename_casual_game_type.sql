-- Spec 011 (ADR-0022): the word "casual" goes away. The game type bowspleef_duel_casual becomes bowspleef_classic.
-- The four tables that reference a game type have foreign keys with no ON UPDATE CASCADE, so the rename is data only:
-- insert the new type, move every reference, delete the old one. On a database that has no old type nothing happens;
-- a bowspleef_classic that an admin already created is kept as it is.
INSERT INTO game_types (name, display_name, ranked, team_size)
SELECT 'bowspleef_classic', 'Bow Spleef Classic', ranked, team_size
FROM game_types
WHERE name = 'bowspleef_duel_casual'
ON CONFLICT (name) DO NOTHING;

UPDATE match          SET game_type = 'bowspleef_classic' WHERE game_type = 'bowspleef_duel_casual';
UPDATE queue          SET game_type = 'bowspleef_classic' WHERE game_type = 'bowspleef_duel_casual';
UPDATE duel_challenge SET game_type = 'bowspleef_classic' WHERE game_type = 'bowspleef_duel_casual';
UPDATE ranked_season  SET game_type = 'bowspleef_classic' WHERE game_type = 'bowspleef_duel_casual';

DELETE FROM game_types WHERE name = 'bowspleef_duel_casual';
