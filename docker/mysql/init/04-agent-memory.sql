USE InfiniteChat;

CREATE TABLE IF NOT EXISTS `agent_turn` (
  `turn_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `request_message_id` bigint DEFAULT NULL,
  `user_prompt` text NOT NULL,
  `assistant_response` mediumtext,
  `status` varchar(16) NOT NULL,
  `model_name` varchar(128) DEFAULT NULL,
  `prompt_tokens` int NOT NULL DEFAULT 0,
  `completion_tokens` int NOT NULL DEFAULT 0,
  `total_tokens` int NOT NULL DEFAULT 0,
  `memory_version` bigint NOT NULL DEFAULT 0,
  `error_message` varchar(1024) DEFAULT NULL,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_time` datetime DEFAULT NULL,
  PRIMARY KEY (`turn_id`),
  UNIQUE KEY `uk_agent_turn_request` (`request_message_id`),
  KEY `idx_agent_turn_owner_session` (`owner_user_id`, `session_id`, `created_time`),
  CONSTRAINT `fk_agent_turn_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_agent_turn_session` FOREIGN KEY (`session_id`) REFERENCES `session` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `agent_tool_trace` (
  `trace_id` bigint NOT NULL AUTO_INCREMENT,
  `turn_id` bigint NOT NULL,
  `tool_call_id` varchar(128) NOT NULL,
  `tool_name` varchar(128) NOT NULL,
  `arguments_json` text,
  `result_inline` mediumtext,
  `result_object_ref` varchar(1024) DEFAULT NULL,
  `result_summary` text,
  `result_sha256` char(64) DEFAULT NULL,
  `result_size_bytes` bigint NOT NULL DEFAULT 0,
  `status` varchar(16) NOT NULL DEFAULT 'RUNNING',
  `duration_ms` bigint NOT NULL DEFAULT 0,
  `error_message` varchar(1024) DEFAULT NULL,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_time` datetime DEFAULT NULL,
  PRIMARY KEY (`trace_id`),
  UNIQUE KEY `uk_agent_tool_call` (`turn_id`, `tool_call_id`),
  KEY `idx_agent_tool_turn` (`turn_id`),
  CONSTRAINT `fk_agent_tool_turn` FOREIGN KEY (`turn_id`) REFERENCES `agent_turn` (`turn_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `agent_memory` (
  `memory_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `session_id` bigint NOT NULL,
  `memory_type` varchar(32) NOT NULL,
  `content` text,
  `normalized_fingerprint` char(64) NOT NULL,
  `confidence` decimal(5,4) NOT NULL,
  `importance` tinyint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `source_start_message_id` bigint DEFAULT NULL,
  `source_end_message_id` bigint DEFAULT NULL,
  `superseded_by` bigint DEFAULT NULL,
  `seen_count` int NOT NULL DEFAULT 1,
  `recall_count` int NOT NULL DEFAULT 0,
  `version` bigint NOT NULL DEFAULT 0,
  `last_seen_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_recalled_time` datetime DEFAULT NULL,
  `expires_time` datetime DEFAULT NULL,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_time` datetime DEFAULT NULL,
  PRIMARY KEY (`memory_id`),
  UNIQUE KEY `uk_agent_memory_fingerprint` (`owner_user_id`, `memory_type`, `normalized_fingerprint`),
  KEY `idx_agent_memory_owner_status` (`owner_user_id`, `status`, `expires_time`),
  KEY `idx_agent_memory_session` (`session_id`, `created_time`),
  CONSTRAINT `fk_agent_memory_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_agent_memory_session` FOREIGN KEY (`session_id`) REFERENCES `session` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `agent_memory_checkpoint` (
  `session_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `last_compacted_turn_id` bigint DEFAULT NULL,
  `last_compacted_message_id` bigint DEFAULT NULL,
  `rolling_summary` mediumtext,
  `version` bigint NOT NULL DEFAULT 0,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`),
  KEY `idx_agent_checkpoint_owner` (`owner_user_id`),
  CONSTRAINT `fk_agent_checkpoint_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_agent_checkpoint_session` FOREIGN KEY (`session_id`) REFERENCES `session` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `agent_memory_setting` (
  `owner_user_id` bigint NOT NULL,
  `capture_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `recall_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `version` bigint NOT NULL DEFAULT 0,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`owner_user_id`),
  CONSTRAINT `fk_agent_setting_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `agent_memory_outbox` (
  `event_id` bigint NOT NULL,
  `memory_id` bigint NOT NULL,
  `operation` varchar(16) NOT NULL,
  `payload_json` json NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT 0,
  `next_retry_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_error` varchar(1024) DEFAULT NULL,
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `published_time` datetime DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  KEY `idx_agent_outbox_poll` (`status`, `next_retry_time`, `created_time`),
  KEY `idx_agent_outbox_memory` (`memory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
