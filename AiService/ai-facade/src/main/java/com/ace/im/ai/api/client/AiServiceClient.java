package com.ace.im.ai.api.client;

import com.ace.im.ai.api.model.dto.ChatRequest;
import com.ace.im.ai.api.model.vo.ChatResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 供其他服务通过Nacos发现并调用AiService；Controller和业务实现位于ai-service模块。 */
@FeignClient(name = "AiService")
public interface AiServiceClient {
    @PostMapping("/api/ai/chat")
    ChatResponse chat(@RequestBody ChatRequest chatRequest);

    /** 聊天记录直接放入UTF-8文本请求体，避免GET查询参数的URL长度限制。 */
    @PostMapping(value = "/api/ai/summary", consumes = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    String chatSummary(@RequestBody String historyLog);
}
