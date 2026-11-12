-- Who asked to cancel an ACTIVE or REPORTED duel; the other participant must agree (spec 003).
-- Existing duels keep a null request.
ALTER TABLE duel_challenge ADD COLUMN cancel_requested_by UUID REFERENCES player(uuid);
