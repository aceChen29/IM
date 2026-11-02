package com.ace.im.offlinedata.model.vo;

import lombok.Data;

@Data
public class MessageSearchVO {
    private String messageId;
    private String sessionId;
    private String senderId;
    private String content;
    private String highlightContent;
    private String createdTime;
}