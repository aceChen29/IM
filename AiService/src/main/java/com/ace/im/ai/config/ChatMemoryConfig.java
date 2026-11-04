package com.ace.im.ai.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {
    @Bean
    public ChatMemoryProvider chatMemoryProvider(
            ChatMemoryStore redisChatMemoryStore) {
        // Qwen远程计数不支持当前系统消息/工具消息组合；使用官方消息窗口。
        // 20限制记忆中的消息条数，不是Token数；Redis继续负责持久化。
        return memoryId ->
                MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(redisChatMemoryStore)
                        .build();
    }
}