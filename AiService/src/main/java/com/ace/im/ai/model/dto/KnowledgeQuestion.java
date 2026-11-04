package com.ace.im.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeQuestion(@NotBlank @Size(max = 1000) String question) {}
