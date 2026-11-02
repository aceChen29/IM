package com.ace.im.user.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 邀请成员入群请求。 */
@Data
public class InviteGroupRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    @NotNull(message = "邀请者ID不能为空")
    private Long inviterId;

    @NotNull(message = "被邀请者ID列表不能为空")
    private List<Long> inviteeIds;
}
