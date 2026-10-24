USE `InfiniteChat`;

CREATE TABLE IF NOT EXISTS `user` (
  `user_id` bigint NOT NULL,
  `phone` char(11) DEFAULT NULL,
  `email` varchar(128) NOT NULL,
  `password` varchar(256) NOT NULL,
  `nickname` varchar(128) NOT NULL,
  `avatar` varchar(512) DEFAULT NULL,
  `gender` tinyint(1) NOT NULL DEFAULT 2,
  `description` text,
  `state` tinyint(1) NOT NULL DEFAULT 0,
  `role` tinyint(1) NOT NULL DEFAULT 0,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `idx_phone` (`phone`),
  UNIQUE KEY `idx_email` (`email`),
  KEY `idx_state` (`state`),
  KEY `idx_role` (`role`),
  KEY `idx_create_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AI 作为 IM 会话中的虚拟参与者存在，保证 user_session/message 外键可复用普通消息模型。
INSERT IGNORE INTO `user`
  (`user_id`, `email`, `password`, `nickname`, `avatar`, `gender`, `state`, `role`, `is_delete`)
VALUES
  (111111111, 'ai@infinitechat.local', 'SYSTEM_ACCOUNT_NO_LOGIN', '千言 AI 助手',
   'https://video.shanyangcode.com/image/default/A9C9C83CCCE043EC8253DB5D7545DCB4-6-2.png', 2, 0, 0, 0);

CREATE TABLE IF NOT EXISTS `session` (
  `session_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `avatar` varchar(512) DEFAULT NULL,
  `type` tinyint NOT NULL,
  `status` tinyint NOT NULL,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_balance` (
  `user_id` bigint NOT NULL,
  `balance` bigint NOT NULL DEFAULT 0,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_balance_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_session` (
  `user_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `role` tinyint NOT NULL,
  `status` tinyint NOT NULL,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `session_id`),
  KEY `idx_session_id` (`session_id`),
  CONSTRAINT `fk_user_session_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_session_session`
    FOREIGN KEY (`session_id`) REFERENCES `session` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `friend` (
  `user_id` bigint NOT NULL,
  `friend_id` bigint NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `friend_id`),
  KEY `idx_friend_id` (`friend_id`),
  CONSTRAINT `fk_friend_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_friend_friend`
    FOREIGN KEY (`friend_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_friend_not_self` CHECK (`user_id` <> `friend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `apply_friend` (
  `apply_friend_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `message` varchar(255) DEFAULT '请求添加好友',
  `status` tinyint NOT NULL DEFAULT 0,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`apply_friend_id`),
  UNIQUE KEY `uk_sender_receiver` (`sender_id`, `receiver_id`),
  KEY `idx_sender_status` (`sender_id`, `status`),
  KEY `idx_receiver_status` (`receiver_id`, `status`),
  CONSTRAINT `fk_apply_sender`
    FOREIGN KEY (`sender_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_apply_receiver`
    FOREIGN KEY (`receiver_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_apply_not_self` CHECK (`sender_id` <> `receiver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `message` (
  `message_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `type` tinyint NOT NULL,
  `content` text NOT NULL,
  `reply_id` bigint DEFAULT NULL,
  `session_type` tinyint NOT NULL,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`message_id`),
  KEY `idx_sender_time` (`sender_id`, `created_time`),
  KEY `idx_session_time` (`session_id`, `created_time`),
  KEY `idx_reply_id` (`reply_id`),
  CONSTRAINT `fk_message_sender`
    FOREIGN KEY (`sender_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_message_session`
    FOREIGN KEY (`session_id`) REFERENCES `session` (`session_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_message_reply`
    FOREIGN KEY (`reply_id`) REFERENCES `message` (`message_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `red_packet` (
  `red_packet_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `session_type` tinyint NOT NULL,
  `red_packet_wrapper_text` varchar(50) NOT NULL DEFAULT '恭喜发财，大吉大利',
  `red_packet_type` tinyint NOT NULL,
  `total_amount` bigint NOT NULL,
  `total_count` int NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`red_packet_id`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_session_id` (`session_id`),
  CONSTRAINT `fk_red_packet_sender`
    FOREIGN KEY (`sender_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_red_packet_session`
    FOREIGN KEY (`session_id`) REFERENCES `session` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `red_packet_receive` (
  `red_packet_receive_id` bigint NOT NULL,
  `red_packet_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `amount` bigint NOT NULL,
  `received_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`red_packet_receive_id`),
  UNIQUE KEY `idx_red_packet_receiver` (`red_packet_id`, `receiver_id`),
  KEY `idx_red_packet_id` (`red_packet_id`),
  KEY `idx_receiver_id` (`receiver_id`),
  CONSTRAINT `fk_red_packet_receive_red_packet`
    FOREIGN KEY (`red_packet_id`) REFERENCES `red_packet` (`red_packet_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_red_packet_receive_receiver`
    FOREIGN KEY (`receiver_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `balance_log` (
  `balance_log_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `amount` bigint NOT NULL,
  `type` tinyint NOT NULL,
  `related_id` bigint DEFAULT NULL,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`balance_log_id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_balance_log_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
