-- Incremental, idempotent schema migration for existing InfiniteChat MySQL volumes.
SOURCE /docker-entrypoint-initdb.d/04-agent-memory.sql;
