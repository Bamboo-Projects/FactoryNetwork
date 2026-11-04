-- For databases created before updates existed (2026-11-04).
ALTER TABLE programs ADD COLUMN updated INTEGER;
ALTER TABLE programs ADD COLUMN key_hash TEXT;
ALTER TABLE programs ADD COLUMN owner TEXT;
