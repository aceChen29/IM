package com.ace.im.user.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 群聊创建后发送给新群员的通知内容。 */
@Data
public class NewGroupSessionNotificationDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String sessionName;
    private String avatar;
    private Long creatorId;
    private Integer membersCount;
}
