package com.ace.im.ai.guardrail;

import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Map;
import java.util.Set;

public class SafeInputGuardrail implements InputGuardrail {

    private static final Set<String> SENSITIVE_WORDS = Set.of("死", "杀");

    // 与原教程一致，只检查用户prompt，避免把RAG检索资料中的词误判为用户输入。
    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        String rawInput = extractRawUserInput(request);
        for (String keyword : SENSITIVE_WORDS) {
            if (!keyword.isEmpty() && rawInput.contains(keyword)) {
                return fatal("提问不能包含敏感词！！！！！");
            }
        }
        return success();
    }

    /** 还原RAG增强之前的用户原始输入。 */
    private String extractRawUserInput(InputGuardrailRequest request) {
        GuardrailRequestParams params = request.requestParams();
        String template = params.userMessageTemplate();
        if (template == null) {
            return request.userMessage().singleText();
        }
        Map<String, Object> vars = params.variables() == null ? Map.of() : params.variables();
        return PromptTemplate.from(template).apply(vars).text();
    }
}
