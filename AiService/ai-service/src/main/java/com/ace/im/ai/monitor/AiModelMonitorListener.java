package com.ace.im.ai.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.service.tool.BeforeToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 模型生命周期监听器：onRequest记开始时间，onResponse/onError记录结果、耗时和Token。
 * DashScope Starter自动把这个Bean注册到模型，无须手动创建第二个ChatModel。
 * 同一次模型请求的回调共用attributes，从中读取身份，完全不依赖ThreadLocal。
 * 工具次数通过单独的官方beforeToolExecution钩子记录，不能用模型请求次数代替。
 */
@Component
@Slf4j
public class AiModelMonitorListener implements ChatModelListener {
    private static final String START_TIME_KEY = "request_start_nanos";
    private static final String MODEL_NAME_KEY = "request_model_name";
    private final AiModelMetricsCollector collector;

    public AiModelMonitorListener(AiModelMetricsCollector collector) {
        this.collector = collector;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put(START_TIME_KEY, System.nanoTime());
        String model = safe(context.chatRequest().modelName());
        context.attributes().put(MODEL_NAME_KEY, model);
        collector.recordRequest(model, "started");
        log.info("AI请求开始 | {} | 模型: {}", identity(context.attributes()), model);
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        // 统一使用请求中的模型名作为指标标签，避免响应返回版本化模型名后无法对齐。
        String model = safe((String) context.attributes().get(MODEL_NAME_KEY));
        long elapsed = elapsed(context.attributes());
        collector.recordRequest(model, "success");
        collector.recordResponseTime(model, "success", elapsed);
        collector.recordTokenUsage(model, context.chatResponse().tokenUsage());
        log.info("AI请求成功 | {} | 模型: {} | 耗时: {}ms | Tokens: {}",
                identity(context.attributes()), model, elapsed / 1_000_000,
                context.chatResponse().tokenUsage());
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        String model = safe((String) context.attributes().get(MODEL_NAME_KEY));
        long elapsed = elapsed(context.attributes());
        collector.recordRequest(model, "error");
        collector.recordError(model, context.error());
        collector.recordResponseTime(model, "error", elapsed);
        log.error("AI请求失败 | {} | 模型: {} | 耗时: {}ms",
                identity(context.attributes()), model, elapsed / 1_000_000, context.error());
    }

    /** ChatService注册此回调；模型提出调用请求后、框架实际执行工具前触发。 */
    public void onBeforeToolExecution(BeforeToolExecution execution, MonitorContext context) {
        collector.recordToolCall(execution.request().name());
        log.info("AI工具调用 | 用户: {} | 会话: {} | 工具: {}",
                context.getUserId(), context.getSessionId(), execution.request().name());
    }

    private long elapsed(Map<Object, Object> attributes) {
        Long start = (Long) attributes.get(START_TIME_KEY);
        return start == null ? 0 : System.nanoTime() - start;
    }

    private String identity(Map<Object, Object> attributes) {
        MonitorContext context = (MonitorContext) attributes.get(MonitorContext.MONITOR_CONTEXT_KEY);
        // 独立知识问答等没有业务身份的调用仍然记录指标，不能因身份缺失直接return。
        return context == null ? "用户: unknown | 会话: unknown"
                : "用户: " + context.getUserId() + " | 会话: " + context.getSessionId();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
