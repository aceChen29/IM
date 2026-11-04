package com.ace.im.ai.aiservice;

import com.ace.im.ai.guardrail.SafeInputGuardrail;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;

/** 根据本次传入的聊天记录生成总结；不绑定聊天记忆、检索或业务工具。 */
@InputGuardrails({SafeInputGuardrail.class})
public interface SummaryAssistant {
    @SystemMessage(fromResource = "system-prompt/chat-summary.txt")
    String chatSummary(@UserMessage String historyLog);
}
