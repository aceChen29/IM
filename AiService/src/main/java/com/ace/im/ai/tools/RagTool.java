package com.ace.im.ai.tools;

import com.ace.im.ai.model.dto.KnowledgeInput;
import com.ace.im.ai.service.KnowledgeService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

/** 自动RAG继续负责回答；额外提供技能所需的主动查重和共享知识写入工具。 */
@Component
@Slf4j
@ConditionalOnProperty(name = "ai.rag.enabled", havingValue = "true")
public class RagTool {
    private final KnowledgeService knowledge;
    private final RetrievalAugmentor retrievalAugmentor;

    public RagTool(KnowledgeService knowledge, RetrievalAugmentor retrievalAugmentor) {
        this.knowledge = knowledge;
        this.retrievalAugmentor = retrievalAugmentor;
    }

    @Tool("在共享知识库中检索相近条目。用于kb-curator技能入库前查重，判断重复、冲突或无关内容。")
    public String searchKnowledge(@P("核心问题或关键词，不要传整段聊天历史") String query) {
        if (query == null || query.isBlank()) {
            return "检索关键词不能为空。";
        }
        try {
            UserMessage message = UserMessage.from(query);
            // 复用当前在线RAG的完整检索+精排流程，而不是只取Milvus精排前的候选。
            List<Content> contents = retrievalAugmentor.augment(new AugmentationRequest(
                    message, Metadata.from(message, null, List.of()))).contents();
            log.info("Tool 调用: 知识库查重 - Q: {} | 命中 {} 条", query, contents.size());
            if (contents.isEmpty()) {
                return "知识库中没有检索到相近条目，可视为新知识。";
            }
            String hits = contents.stream().map(c -> c.textSegment().text())
                    .collect(Collectors.joining("\n\n--------\n\n"));
            return "检索到 " + contents.size() + " 条相近内容，请判断是重复、冲突还是无关：\n\n" + hits;
        } catch (Exception e) {
            log.error("知识库查重失败: {}", query, e);
            return "知识库检索失败：" + e.getMessage() + "。请如实告知用户，不要在没查重的情况下直接入库。";
        }
    }

    @Tool("仅当用户明确要求发布到共享知识库时调用。先加载kb-curator技能并按说明查重，再保存用户提供的知识；写入后其他用户也能检索。不能自动发布私人聊天或模型猜测。")
    public String addSharedKnowledge(String title, String text) {
        return knowledge.ingest(new KnowledgeInput(title, text));
    }
}
