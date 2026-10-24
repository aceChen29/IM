-- Existing volumes do not rerun docker/mysql/init scripts. This migration is idempotent.
INSERT IGNORE INTO `InfiniteChat`.`user`
  (`user_id`, `email`, `password`, `nickname`, `avatar`, `gender`, `state`, `role`, `is_delete`)
VALUES
  (111111111, 'ai@infinitechat.local', 'SYSTEM_ACCOUNT_NO_LOGIN', '千言 AI 助手',
   'https://video.shanyangcode.com/image/default/A9C9C83CCCE043EC8253DB5D7545DCB4-6-2.png', 2, 0, 0, 0);
