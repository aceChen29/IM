package com.ace.im.user.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 创建群聊请求。 */
@Data
public class CreateGroupRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "创建者ID不能为空")
    private Long creatorId;

    @NotNull(message = "成员ID列表不能为空")
    private List<Long> memberIds;
}
