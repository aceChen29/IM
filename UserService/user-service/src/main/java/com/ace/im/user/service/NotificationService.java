package com.ace.im.user.service;

import com.ace.im.user.model.dto.FriendApplicationNotificationDTO;
import com.ace.im.user.model.dto.NewSessionNotificationDTO;

/** 发送用户服务产生的系统通知。 */
public interface NotificationService {

    /** 向接收者发送好友申请通知。 */
    void pushNewApply(Long receiverId, FriendApplicationNotificationDTO notification);

    /** 好友申请通过后通知申请者新单聊会话。 */
    void pushNewSession(Long senderId, Long receiverId, Long sessionId, Integer sessionType,
                        NewSessionNotificationDTO notification);
}
