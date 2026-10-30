package com.ace.im.user.service.impl;

import cn.hutool.json.JSONUtil;
import com.ace.im.common.constant.MessageTypeConstant;
import com.ace.im.user.constants.KafkaTopicConstant;
import com.ace.im.user.model.dto.FriendApplicationNotificationDTO;
import com.ace.im.user.model.dto.NewSessionNotificationDTO;
import com.ace.im.user.model.dto.SystemNotificationMessage;
import com.ace.im.user.service.NotificationService;
import com.ace.im.user.utils.SnowflakeDynamicUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** 将用户服务的系统通知组装后发送到 Kafka。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SnowflakeDynamicUtil snowflakeDynamicUtil;

    @Override
    public void pushNewApply(Long userId, FriendApplicationNotificationDTO notification) {
        try {
            SystemNotificationMessage message = new SystemNotificationMessage();
            message.setMessageId(generateMessageId());
            message.setSessionId(null); // 好友申请不属于任何会话
            message.setSenderId(notification.getApplyUserId());
            message.setReceiverId(userId);
            message.setType(MessageTypeConstant.TYPE_SYSTEM_NEW_APPLY);
            message.setSessionType(null);
            message.setTimestamp(System.currentTimeMillis());

            Map<String, Object> body = new HashMap<>();
            body.put("nickname", notification.getApplyUserName());
            body.put("avatar", notification.getApplyFriendAvatar());
            body.put("msg", notification.getMessage());
            message.setBody(body);

            sendNotification(message, "好友申请通知");
        } catch (Exception e) {
            log.error("发送好友申请通知失败，用户ID={}", userId, e);
        }
    }

    @Override
    public void pushNewSession(Long senderId, Long receiverId, Long sessionId, Integer sessionType,
                               NewSessionNotificationDTO notification) {
        try {
            SystemNotificationMessage message = new SystemNotificationMessage();
            message.setMessageId(generateMessageId());
            message.setSessionId(sessionId);
            message.setSenderId(senderId);
            message.setReceiverId(receiverId);
            message.setType(MessageTypeConstant.TYPE_SYSTEM_NEW_SESSION);
            message.setSessionType(sessionType);
            message.setTimestamp(System.currentTimeMillis());

            Map<String, Object> body = new HashMap<>();
            body.put("sessionName", notification.getSessionName());
            body.put("avatar", notification.getAvatar());
            message.setBody(body);
            sendNotification(message, "新会话通知");
        } catch (Exception e) {
            log.error("发送新会话通知失败，用户ID={}，会话ID={}", receiverId, sessionId, e);
        }
    }

    /** 生成系统通知的唯一消息 ID。 */
    private String generateMessageId() {
        return "msg_" + System.currentTimeMillis() + "_" + snowflakeDynamicUtil.nextId();
    }

    /** 以接收者 ID 为 Kafka key，同一接收者的通知进入同一个分区。 */
    private void sendNotification(SystemNotificationMessage message, String notificationName) {
        kafkaTemplate.send(
                KafkaTopicConstant.TOPIC_SYSTEM_NOTIFICATION,
                String.valueOf(message.getReceiverId()),
                JSONUtil.toJsonStr(message)
        ).whenComplete((result, error) -> {
            if (error == null) {
                log.info("{}发送成功，messageId={}, 用户ID={}, type={}",
                        notificationName, message.getMessageId(), message.getReceiverId(), message.getType());
            } else {
                log.error("{}发送失败，messageId={}, 用户ID={}",
                        notificationName, message.getMessageId(), message.getReceiverId(), error);
            }
        });
    }
}
