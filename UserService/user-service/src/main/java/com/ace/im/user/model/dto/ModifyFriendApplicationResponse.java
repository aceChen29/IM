package com.ace.im.user.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 好友申请通过后返回给接收者的单聊会话信息。 */
@Data
public class ModifyFriendApplicationResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String userId;
    private String sessionId;
    private Integer sessionType;
    private String sessionName;
    private String avatar;
}
