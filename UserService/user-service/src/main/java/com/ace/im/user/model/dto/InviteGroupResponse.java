package com.ace.im.user.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 邀请成员入群结果。 */
@Data
public class InviteGroupResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private List<String> successIds;
    private List<String> failedIds;
}
