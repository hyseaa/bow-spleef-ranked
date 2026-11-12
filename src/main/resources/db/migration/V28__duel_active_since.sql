-- The moment a duel last became ACTIVE (acceptance, or a dispute that put it back), used to cancel a duel that
-- nobody plays (spec 006). Duels that are ACTIVE now start their clock at the migration, so that nothing is
-- cancelled by surprise on the day of the deployment.
ALTER TABLE duel_challenge ADD COLUMN active_since TIMESTAMPTZ;

UPDATE duel_challenge SET active_since = NOW() WHERE status = 'ACTIVE';
