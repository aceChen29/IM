package com.ace.im.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** AI聊天请求。 */
@Data
public class ChatRequest {
    @NotBlank
    @Pattern(regexp = "[0-9]{1,19}")
    private String userId;
    @NotBlank
    @Pattern(regexp = "[0-9]{1,19}")
    private String sessionId;

    @NotBlank
    @Size(max = 8000)
    private String prompt;
}
