-- Safe to run more than once on databases created before session.avatar was added.
SET @avatar_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = 'InfiniteChat'
    AND table_name = 'session'
    AND column_name = 'avatar'
);

SET @avatar_migration_sql = IF(
  @avatar_column_exists = 0,
  'ALTER TABLE `InfiniteChat`.`session` ADD COLUMN `avatar` varchar(512) DEFAULT NULL AFTER `name`',
  'SELECT 1'
);

PREPARE avatar_migration_statement FROM @avatar_migration_sql;
EXECUTE avatar_migration_statement;
DEALLOCATE PREPARE avatar_migration_statement;
