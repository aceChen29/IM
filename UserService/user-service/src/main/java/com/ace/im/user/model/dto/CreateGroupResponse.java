package com.ace.im.user.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 创建群聊响应。对前端返回的 ID 均为字符串。 */
@Data
public class CreateGroupResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String creatorId;
    private String sessionId;
    private String sessionName;
    private Integer sessionType;
    private String avatar;
    private List<String> failedMemberIds;
}
