-- Apply once to an existing InfiniteChat database created before session_type was added.
-- Select the target database before running this statement.
ALTER TABLE `red_packet`
  ADD COLUMN `session_type` tinyint NOT NULL AFTER `session_id`;
