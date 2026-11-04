package com.ace.im.ai.monitor;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 流式模型的监控适配器：每次业务请求绑定自己的用户、会话身份，复用Starter创建的模型。
 * 在调用时通过官方listenerAttributes传入身份，开始、完成、错误监听器均可读取。
 * 流式响应可以在其他线程回调，不依赖ThreadLocal，也不需要在线程上set/remove。
 * 每轮工具调用后的模型请求仍会经过这里，各轮独立计时，身份保持一致。
 */
public class MonitoredStreamingChatModel implements StreamingChatModel {
    private final StreamingChatModel delegate;
    private final MonitorContext monitorContext;

    public MonitoredStreamingChatModel(
            StreamingChatModel delegate, MonitorContext monitorContext) {
        this.delegate = delegate;
        this.monitorContext = monitorContext;
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        chat(request, ChatRequestOptions.EMPTY, handler);
    }

    @Override
    public void chat(
            ChatRequest request, ChatRequestOptions options,
            StreamingChatResponseHandler handler) {
        // 复制已有属性，再补充本次请求的身份，不修改调用方的Map。
        Map<Object, Object> attributes = new HashMap<>();
        if (options != null) {
            attributes.putAll(options.listenerAttributes());
        }
        attributes.put(MonitorContext.MONITOR_CONTEXT_KEY, monitorContext);
        // 框架负责将同一份调用属性传给监听器，handler负责接收文本片段和完成/错误通知。
        delegate.chat(request, ChatRequestOptions.builder()
                .listenerAttributes(attributes).build(), handler);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
