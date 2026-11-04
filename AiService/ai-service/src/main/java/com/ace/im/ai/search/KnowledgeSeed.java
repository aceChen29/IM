package com.ace.im.ai.search;


import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import com.ace.im.ai.service.KnowledgeService;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;


@Component
@Profile("knowledge-seed")
public class KnowledgeSeed implements CommandLineRunner {
    private final KnowledgeService service;

    public KnowledgeSeed(KnowledgeService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) throws Exception {
        // 官方classpath加载器与显式UTF-8纯文本解析器；打包后也能读取Markdown。
        // 显式传解析器，不依赖SPI默认实现，不需Easy RAG或Tika依赖。
        Document document = ClassPathDocumentLoader.loadDocument("InfiniteChat.md", new TextDocumentParser());
        String docId = service.ingest(document, "消息索引同步");
        System.out.println("知识入库完成，docId=" + docId);
    }
}
