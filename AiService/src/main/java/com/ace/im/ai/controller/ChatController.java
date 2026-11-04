package com.ace.im.ai.controller;

import com.ace.im.ai.model.dto.ChatRequest;
import com.ace.im.ai.model.vo.ChatResponse;
import com.ace.im.ai.service.ChatService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.nio.charset.StandardCharsets;
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/ai")
public class ChatController {
    private final ChatService chatService;


    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest chatRequest) {
        return new ChatResponse(chatService.chat(chatRequest));
    }

    /** SSE逐段输出文本，显式声明UTF-8，确保中文的写出和客户端解码使用同一字符集。 */
    @PostMapping(value = "/streamChat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> streamChat(
            @Valid @RequestBody ChatRequest chatRequest, HttpServletResponse response) {
        // 提前设置Content-Type和编码，供SSE的字符串转换器及客户端共同使用UTF-8。
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return chatService.streamChat(chatRequest);
    }
}
