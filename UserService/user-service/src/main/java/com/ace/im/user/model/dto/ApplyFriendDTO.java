package com.ace.im.user.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 好友申请列表项。 */
@Data
public class ApplyFriendDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 对方用户 ID（发送者或接收者）。 */
    private String userId;
    private String nickname;
    private String avatar;
    private String msg;
    /** 0 未读，1 通过，2 拒绝，3 已读，4 过期。 */
    private Integer status;
    private LocalDateTime time;
    /** 0 我是发送者，1 我是接收者。 */
    private Integer isReceiver;
}
