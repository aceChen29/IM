package com.ace.im.ai.config;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Arrays;

@Configuration
@ConditionalOnProperty(name = "ai.mcp.enabled", havingValue = "true")
public class McpConfig {
    @Bean(destroyMethod = "close")
    public McpClient mcpClient(
            @Value("${ai.mcp.url}") String url,
            @Value("${ai.mcp.api-key}") String apiKey) {
        if (apiKey.isBlank()) throw new IllegalArgumentException("启用天气MCP前，请先配置AMAP_API_KEY");
        // 高德MCP使用URL中的key参数鉴权；它不是Qwen API Key，也不是Bearer Token。
        String endpoint = UriComponentsBuilder.fromUriString(url)
                .queryParam("key", apiKey.trim())
                .build().encode().toUriString();
        var transport = new StreamableHttpMcpTransport.Builder()
                .url(endpoint) // 官方Streamable HTTP地址，不是旧版/sse地址。
                .timeout(Duration.ofSeconds(20)) // 单次HTTP请求超时。
                .logRequests(false) // URL包含Key，不输出请求日志。
                .logResponses(false)
                .build();
        return new DefaultMcpClient.Builder()
                .key("amap-weather") // 本地客户端标识，不是API Key。
                .transport(transport)
                .initializationTimeout(Duration.ofSeconds(20)) // initialize握手等待上限。
                .toolExecutionTimeout(Duration.ofSeconds(15)) // 单次远程工具执行等待上限。
                .build();
    }

    @Bean
    public McpToolProvider mcpToolProvider(
            McpClient client,
            @Value("${ai.mcp.allowed-tools:maps_weather}") String allowedTools) {
        String[] names = Arrays.stream(allowedTools.split(","))
                .map(String::trim).filter(name -> !name.isBlank()).toArray(String[]::new);
        if (names.length == 0) throw new IllegalArgumentException("启用MCP时必须配置工具白名单");
        return McpToolProvider.builder()
                .mcpClients(client) // 从服务发现工具定义，按模型调用请求执行远程工具。
                .filterToolNames(names) // 只向模型暴露天气工具，保留工具白名单。
                .build();
    }
}
