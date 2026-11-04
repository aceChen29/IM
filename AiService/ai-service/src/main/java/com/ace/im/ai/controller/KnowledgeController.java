package com.ace.im.ai.controller;

import com.ace.im.ai.api.model.dto.KnowledgeInput;
import com.ace.im.ai.api.model.dto.KnowledgeQuestion;
import com.ace.im.ai.api.model.vo.ChatResponse;
import com.ace.im.ai.service.KnowledgeService;
import com.ace.im.ai.service.KnowledgeAnswerService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/knowledge")
@ConditionalOnProperty(name = "ai.rag.enabled", havingValue = "true")
public class KnowledgeController {
    private final KnowledgeService knowledgeService;
    private final KnowledgeAnswerService answerService;

    public KnowledgeController(KnowledgeService knowledgeService, KnowledgeAnswerService answerService) {
        this.knowledgeService = knowledgeService;
        this.answerService = answerService;
    }

    @PostMapping("/documents")
    public Map<String, String> ingest(@Valid @RequestBody KnowledgeInput input) {
        return Map.of("docId", knowledgeService.ingest(input));
    }

    @PostMapping("/ask")
    public ChatResponse ask(@Valid @RequestBody KnowledgeQuestion request) {
        return new ChatResponse(answerService.answer(request.question()));
    }
}
