package com.ace.im.user.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 通过、拒绝或批量标记已读的申请人 ID。 */
@Data
public class ModifyFriendApplicationRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "接收者ID列表不能为空")
    private List<String> receiveuserIds;
}
