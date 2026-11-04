package com.ace.im.ai.monitor;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次业务请求的监控身份，由ChatService从ChatRequest创建。
 * 使用不可变字段，随调用显式传递，不绑定线程，也不写进发给模型的提示词。
 * 用户和会话只用于日志关联，不作为Prometheus标签，避免指标数量随用户增长。
 *
 * 监控某个会话userId
 */
@Getter
@AllArgsConstructor
public class MonitorContext {
    public static final String MONITOR_CONTEXT_KEY = "monitor_context";

    private final String userId;
    private final String sessionId;
}
