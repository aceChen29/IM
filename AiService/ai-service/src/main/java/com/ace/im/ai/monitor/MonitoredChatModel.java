package com.ace.im.ai.monitor;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 连接业务身份与官方模型监听器的薄适配器，每次业务请求创建一个实例。
 * ChatModelListener的attributes不会自动知道userId/sessionId，必须显式传入。
 * 使用当前版本官方ChatRequestOptions.listenerAttributes传递，模型会复制这些属性到
 * 本轮模型调用的attributes，再交给请求、响应、错误回调；多轮工具调用也各自计时。
 * 这里只复用底层ChatModel，不创建网络客户端、不修改提示词、不把身份存入共享可变字段。
 * 这是同步ChatModel的适配器；流式入口使用MonitoredStreamingChatModel做同样的显式传递。
 */
public class MonitoredChatModel implements ChatModel {
    private final ChatModel delegate;
    private final MonitorContext monitorContext;

    public MonitoredChatModel(ChatModel delegate, MonitorContext monitorContext) {
        this.delegate = delegate;
        this.monitorContext = monitorContext;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        // 调用方没有传选项时，使用空选项进入下面的重载方法。
        // EMPTY是一个非null对象，只是其中的监听器属性Map为空。
        return chat(request, ChatRequestOptions.EMPTY);
    }

    @Override
    public ChatResponse chat(ChatRequest request, ChatRequestOptions options) {
        // 每次调用新建Map，组装要传给已有监听器的附加数据，不修改调用方的选项。
        Map<Object, Object> attributes = new HashMap<>();
        // 调用方可能已经传入traceId等属性，用putAll保留这些键值。
        // options非null不代表有属性：传入EMPTY时，这里复制的是空Map。
        // 判空也兼容调用方直接调用chat(request, null)的情况。
        if (options != null) {
            attributes.putAll(options.listenerAttributes());
        }
        // 再加入本次请求的用户/会话身份；若已有同名键，以当前monitorContext为准。
        attributes.put(MonitorContext.MONITOR_CONTEXT_KEY, monitorContext);
        // 用合并后的属性创建新选项；真实模型会把它们交给监听器的attributes()。
        // 这里传递监听器数据，不负责注册监听器，也不会把这些属性加入模型提示词。
        return delegate.chat(request, ChatRequestOptions.builder()
                .listenerAttributes(attributes).build());
    }
//保留真实模型的配置、供应商信息和能力声明
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
