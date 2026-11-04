package com.ace.im.ai.config;

import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "ai.rag.enabled", havingValue = "true")
public class RagConfig {
    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever, ScoringModel scoringModel) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever) // 注入实现官方接口的KnowledgeService。
                .contentAggregator(ReRankingContentAggregator.builder()
                        .scoringModel(scoringModel) // Starter创建的qwen3-rerank，对候选原文做语义评分。
                        .maxResults(5) // 精排后最多5条；不手写分数排序和截断算法。
                        .build())
                .contentInjector(DefaultContentInjector.builder()
                        // 正文默认注入；额外带上真实chunk_id、title，供模型引用来源。
                        .metadataKeysToInclude(List.of("chunk_id", "title"))
                        .build())
                .build(); // 由官方组件完成问题、检索和证据注入流程。
    }
}
