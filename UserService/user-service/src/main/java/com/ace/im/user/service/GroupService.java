package com.ace.im.user.service;

import com.ace.im.user.model.dto.InviteGroupRequest;
import com.ace.im.user.model.dto.InviteGroupResponse;

/** 在已有群聊中管理成员。 */
public interface GroupService {
    InviteGroupResponse inviteGroup(InviteGroupRequest request);
}
