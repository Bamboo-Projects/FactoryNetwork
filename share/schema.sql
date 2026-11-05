-- Programs shared by code. No column holds anything about the uploader.
CREATE TABLE IF NOT EXISTS programs (
  code TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  files TEXT NOT NULL,
  file_count INTEGER NOT NULL,
  lines INTEGER NOT NULL,
  created INTEGER NOT NULL,
  downloads INTEGER NOT NULL DEFAULT 0,
  reports INTEGER NOT NULL DEFAULT 0,
  hidden INTEGER NOT NULL DEFAULT 0,
  updated INTEGER,
  -- SHA-256 of the disk's secret key; the key itself is never stored.
  key_hash TEXT,
  -- Only when the uploader chose to bind: the Mojang-confirmed UUID.
  owner TEXT
);
CREATE INDEX IF NOT EXISTS programs_created ON programs (hidden, created);
CREATE INDEX IF NOT EXISTS programs_downloads ON programs (hidden, downloads);
