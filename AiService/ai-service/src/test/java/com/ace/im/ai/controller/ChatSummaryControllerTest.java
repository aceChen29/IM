package com.ace.im.ai.controller;

import com.ace.im.ai.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 验证Controller与facade一致使用POST文本请求体，不调用模型。 */
class ChatSummaryControllerTest {
    private ChatService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ChatService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(service)).build();
    }

    @Test
    void acceptsLongUtf8BodyAndReturnsPlainText() throws Exception {
        String history = "[张三] 接口参数：x=1&y=2+3\n[李四] 我负责测试。\n".repeat(500);
        when(service.chatSummary(history)).thenReturn("张三开发接口，李四负责测试。");

        var response = mvc.perform(post("/api/ai/summary")
                .contentType("text/plain;charset=UTF-8")
                .content(history.getBytes(StandardCharsets.UTF_8))).andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("张三开发接口，李四负责测试。", response.getContentAsString());
        verify(service).chatSummary(history);
    }

    @Test
    void rejectsGetAndMissingBodyAndJson() throws Exception {
        assertEquals(405, mvc.perform(get("/api/ai/summary").param("historyLog", "聊天记录"))
                .andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(post("/api/ai/summary").contentType("text/plain;charset=UTF-8"))
                .andReturn().getResponse().getStatus());
        assertEquals(415, mvc.perform(post("/api/ai/summary").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historyLog\":\"聊天记录\"}"))
                .andReturn().getResponse().getStatus());
        verifyNoInteractions(service);
    }
}
