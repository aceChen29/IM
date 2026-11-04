package com.ace.im.ai.api.client;

import com.ace.im.ai.api.model.dto.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 通过真实HTTP验证facade的参数编码及返回类型，不调用模型或外部服务。 */
class AiServiceClientTest {
    private HttpServer server;
    private AnnotationConfigApplicationContext context;
    private AiServiceClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        context = new AnnotationConfigApplicationContext(FeignConfiguration.class);
        client = new FeignClientBuilder(context).forType(AiServiceClient.class, "AiService")
                .url("http://127.0.0.1:" + server.getAddress().getPort()).build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        if (context != null) context.close();
    }

    @Test
    void summaryPostsUtf8HistoryInBodyAndDecodesPlainText() {
        AtomicReference<String> received = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/api/ai/summary", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            method.set(exchange.getRequestMethod());
            query.set(exchange.getRequestURI().getRawQuery());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] body = "张三负责开发，李四负责测试。".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        String history = "[张三] 开发接口：x=1&y=2+3\n[李四] 我负责测试。\n".repeat(500);

        assertEquals("张三负责开发，李四负责测试。", client.chatSummary(history));
        assertEquals(history, received.get());
        assertEquals("POST", method.get());
        assertNull(query.get());
        assertEquals("text/plain;charset=UTF-8", contentType.get());
    }

    @Test
    void chatSendsSharedDtoAndDecodesChatResponse() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        server.createContext("/api/ai/chat", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"answer\":\"你好\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        ChatRequest request = new ChatRequest();
        request.setUserId("101");
        request.setSessionId("201");
        request.setPrompt("你好");

        assertEquals("你好", client.chat(request).getAnswer());
        var json = new ObjectMapper().readTree(received.get());
        assertEquals("101", json.get("userId").asText());
        assertEquals("201", json.get("sessionId").asText());
        assertEquals("你好", json.get("prompt").asText());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({FeignAutoConfiguration.class, JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class})
    static class FeignConfiguration {}
}
