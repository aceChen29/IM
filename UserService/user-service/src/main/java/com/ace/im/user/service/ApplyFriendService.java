package com.ace.im.user.service;

import com.ace.im.common.model.dto.PageRequest;
import com.ace.im.user.model.dto.ApplyFriendDTO;
import com.ace.im.user.model.dto.ModifyFriendApplicationResponse;
import com.ace.im.user.model.entity.ApplyFriend;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.validation.constraints.NotBlank;

/**
* @author cy
* @description 针对表【apply_friend】的数据库操作Service
* @createDate 2026-10-28 21:10:51
*/
public interface ApplyFriendService extends IService<ApplyFriend> {

    Long sendFriendRequest(Long senderId, Long receiverId, @NotBlank(message = "申请消息不能为空") String msg);

    /** 查询用户参与的好友申请，附带对方的用户信息。 */
    IPage<ApplyFriendDTO> getReceivedRequestsWithUserInfo(Long userId, PageRequest pageRequest);

    /** 统计当前用户收到且尚未阅读的好友申请。 */
    int getUnreadCount(Long userId);

    /** 接收方通过、拒绝或标记已读指定申请人的好友申请。 */
    ModifyFriendApplicationResponse modifyApplicationStatus(Long receiverId,
                                                              java.util.List<Long> senderIds,
                                                              Integer status);
}
