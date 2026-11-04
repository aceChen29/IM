package com.ace.im.ai.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * title是知识标题，text是待切分、向量化的正文。
 * 不包含userId，也不是Milvus或LangChain4j内置类型。
 */
public record KnowledgeInput(
        // 知识标题，例如“消息索引同步”；不是文件路径。
        @NotBlank @Size(max = 200) String title,
        // 纯文本正文；离线阶段先读取或解析文件，再把正文放在这里。
        @NotBlank @Size(max = 16000) String text) {}
