package com.ace.im.user.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 新单聊会话系统通知的 body。 */
@Data
public class NewSessionNotificationDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String sessionName;
    private String avatar;
}
