package com.ace.im.ai.service;

import com.ace.im.ai.aiservice.KnowledgeAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "ai.rag.enabled", havingValue = "true")
public class KnowledgeAnswerService {
    private final KnowledgeAssistant assistant;

    public KnowledgeAnswerService(ChatModel chatModel, RetrievalAugmentor retrievalAugmentor) {
        this.assistant = AiServices.builder(KnowledgeAssistant.class)
                .chatModel(chatModel) // 复用Starter创建的模型。
                .retrievalAugmentor(retrievalAugmentor) // 自动检索和注入证据，不手动拼接JSON。
                .build();
    }

    public String answer(String question) {
        var result = assistant.answer(question);
        // sources()是真实检索来源；没有来源时不把模型常识冒充知识库证据。
        // 此检查在模型调用之后，空检索仍可能发生一次模型调用。
        return result.sources() == null || result.sources().isEmpty()
                ? "在共享知识库中没有找到相关资料" : result.content();
    }
}
