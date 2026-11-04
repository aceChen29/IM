package com.ace.im.ai.monitor;

import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 统一定义指标名称、标签和单位，不负责模型调用。
 * 保留旧版Counter统计次数/Token、Timer统计耗时的思路。
 * MeterRegistry按指标名+标签复用指标，因此不再自行维护四个Counter/Timer缓存。
 * 每次模型推理单独计数：一次HTTP聊天可能包含多次模型推理和工具执行。
 */
@Component
public class AiModelMetricsCollector {
    private final MeterRegistry meterRegistry;

    public AiModelMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest(String modelName, String status) {
        Counter.builder("ai_model_requests_total")
                .description("Model calls by lifecycle status")
                .tag("model_name", safe(modelName)).tag("status", status)
                .register(meterRegistry).increment();
    }

    public void recordError(String modelName, Throwable error) {
        Counter.builder("ai_model_errors_total")
                .description("Failed model calls")
                .tag("model_name", safe(modelName))
                // 不把异常全文作为标签：异常全文可能包含请求ID，造成指标数量持续增长。
                .tag("error_type", error.getClass().getSimpleName())
                .register(meterRegistry).increment();
    }

    public void recordTokenUsage(String modelName, TokenUsage usage) {
        if (usage == null) return; // 部分供应商/错误响应不提供Token用量，不编造数值。
        recordTokens(modelName, "input", usage.inputTokenCount());
        recordTokens(modelName, "output", usage.outputTokenCount());
        recordTokens(modelName, "total", usage.totalTokenCount());
    }

    private void recordTokens(String modelName, String type, Integer count) {
        if (count == null || count < 0) return;
        Counter.builder("ai_model_tokens_total")
                .description("Tokens reported by model provider")
                .tag("model_name", safe(modelName)).tag("token_type", type)
                .register(meterRegistry).increment(count);
    }

    public void recordResponseTime(String modelName, String status, long elapsedNanos) {
        Timer.builder("ai_model_response_duration_seconds")
                .description("Duration of a model call, excluding business RAG/tool processing")
                .tag("model_name", safe(modelName)).tag("status", status)
                .register(meterRegistry).record(Math.max(0, elapsedNanos), TimeUnit.NANOSECONDS);
    }

    /** 在官方beforeToolExecution回调计数，表示执行尝试，不等同于工具业务成功。 */
    public void recordToolCall(String toolName) {
        Counter.builder("ai_tool_calls_total")
                .description("Tool execution attempts, including local and MCP tools")
                .tag("tool_name", safe(toolName))
                .register(meterRegistry).increment();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
