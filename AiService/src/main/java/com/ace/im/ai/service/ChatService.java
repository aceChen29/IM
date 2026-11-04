package com.ace.im.ai.service;

import com.ace.im.ai.aiservice.AssistantService;
import com.ace.im.ai.monitor.AiModelMonitorListener;
import com.ace.im.ai.monitor.MonitorContext;
import com.ace.im.ai.monitor.MonitoredChatModel;
import com.ace.im.ai.monitor.MonitoredStreamingChatModel;
import com.ace.im.ai.model.dto.ChatRequest;
import com.ace.im.ai.tools.RagTool;
import com.ace.im.ai.tools.EmailTool;
import com.ace.im.ai.skill.SkillTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.mcp.McpToolProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {
    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final RetrievalAugmentor retrievalAugmentor;
    private final RagTool ragTool;
    private final EmailTool emailTool;
    private final SkillTool skillTool;
    private final ObjectProvider<McpToolProvider> mcpToolProvider;
    private final AiModelMonitorListener monitorListener;

    public ChatService(
            ChatModel chatModel,
            StreamingChatModel streamingChatModel,
            ChatMemoryProvider chatMemoryProvider,
            RetrievalAugmentor retrievalAugmentor,
            RagTool ragTool,
            EmailTool emailTool,
            SkillTool skillTool,
            ObjectProvider<McpToolProvider> mcpToolProvider,
            AiModelMonitorListener monitorListener) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.retrievalAugmentor = retrievalAugmentor;
        this.ragTool = ragTool;
        this.emailTool = emailTool;
        this.skillTool = skillTool;
        this.mcpToolProvider = mcpToolProvider;
        this.monitorListener = monitorListener;
    }

    public String chat(ChatRequest chatRequest) {
        requireId(chatRequest.getUserId());
        MonitorContext context = new MonitorContext(chatRequest.getUserId(), chatRequest.getSessionId());
        return createAssistant(context).chat(chatRequest.getSessionId(), chatRequest.getPrompt());
    }

    public Flux<String> streamChat(ChatRequest chatRequest) {
        // 等订阅时才创建本次调用的上下文和助手，生成的文本片段由框架持续发布。
        return Flux.defer(() -> {
            requireId(chatRequest.getUserId());
            MonitorContext context = new MonitorContext(chatRequest.getUserId(), chatRequest.getSessionId());
            return createAssistant(context).streamChat(chatRequest.getSessionId(), chatRequest.getPrompt());
        });
    }

    private AssistantService createAssistant(MonitorContext context) {
        // 每次业务调用独立绑定监控身份，底层模型、Redis记忆和工具仍复用。
        // 不往共享单例助手写入currentUser字段，避免并发请求互相覆盖。

        var builder = AiServices.builder(AssistantService.class)
                .chatModel(new MonitoredChatModel(chatModel, context))
                .streamingChatModel(new MonitoredStreamingChatModel(streamingChatModel, context))
                .chatMemoryProvider(chatMemoryProvider)
                // 与原版一致，每轮聊天自动检索、精排并注入知识。
                .retrievalAugmentor(retrievalAugmentor)
                // 第一层只注入技能名和描述；完整规程由模型调用loadSkill按需读取。
                .systemMessageTransformer(sm -> sm + "\n\n" + skillTool.catalog())
                .tools(ragTool, emailTool, skillTool)
                .beforeToolExecution(execution -> monitorListener.onBeforeToolExecution(execution, context))
                .maxToolCallingRoundTrips(6);
        mcpToolProvider.ifAvailable(builder::toolProvider);
        return builder.build();
    }

    public static void requireId(String id) {
        if (id == null || !id.matches("[0-9]{1,19}")) {
            throw new IllegalArgumentException("用户ID格式错误");
        }
        Long.parseLong(id);
    }
}
