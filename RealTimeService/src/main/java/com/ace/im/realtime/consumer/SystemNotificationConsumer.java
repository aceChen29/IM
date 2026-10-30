package com.ace.im.realtime.consumer;

import java.util.Map;



import cn.hutool.json.JSONUtil;
import com.ace.im.common.constant.CommonConstant;
import com.ace.im.realtime.utils.ChannelManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 系统通知消息消费者
 * <p>
 * 功能说明：
 * - 消费UserService发送的系统通知消息
 * - 通过WebSocket直接转发完整的消息给在线用户
 * - 符合IM项目通知消息设计方案
 * - 支持多种类型的系统通知（新会话、好友申请、群聊邀请等）
 * <p>
 * 消息类型：
 * - 101：收到好友申请通知
 * - 102：新会话创建通知
 * - 103：新群聊会话创建通知
 */
@Slf4j
@Component
public class SystemNotificationConsumer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public SystemNotificationConsumer(KafkaTemplate<String, String> kafkaTemplate,
                                      StringRedisTemplate stringRedisTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 消费系统通知消息
     * <p>
     * 处理流程：
     * 1. 接收 Kafka 消息（完整的 SystemNotificationMessage JSON）
     * 2. 解析消息获取接收者ID
     * 3. 根据接收者ID查找对应的 WebSocket Channel
     * 4. 转发完整消息到 WebSocket
     * 5. 发送到 Kafka 的 store-notification-topic 进行持久化
     * <p>
     * 消息格式：
     * {
     * "messageId": "msg_1699999999000_123456789",
     * "sessionId": 123,
     * "senderId": 0,
     * "receiverId": 1,
     * "type": 102,
     * "sessionType": 0,
     * "timestamp": 1699999999000,
     * "body": {
     * "sessionName": "张三",
     * "avatar": "http://..."
     * }
     * }
     *
     * @param message Kafka消息（完整的SystemNotificationMessage JSON）
     */
    @KafkaListener(
            topics = CommonConstant.KAFKA_SYSTEM_NOTIFICATION_TOPIC,
            groupId = "system-notification-consumer-group",
            concurrency = "3"
    )
    public void consumeSystemNotification(String message) {
        try {
            log.debug("收到系统通知消息: {}", message);

            // 1. 解析消息获取接收者ID和messageId（用于日志）
            Map<String, Object> notificationMap = JSONUtil.toBean(message, Map.class);
            String messageId = (String) notificationMap.get("messageId");
            Integer type = (Integer) notificationMap.get("type");
            Long receiverId = Long.parseLong(notificationMap.get("receiverId").toString());

            // 2. 获取用户的WebSocket Channel（使用静态方法）
            Channel channel = ChannelManager.getChannelByUserId(String.valueOf(receiverId));

            // 3. 本实例持有接收者的有效连接时，实时推送通知
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(message)).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        log.info("系统通知推送成功，messageId: {}, receiverId: {}, type: {}",
                                messageId, receiverId, type);
                    } else {
                        log.error("系统通知推送失败，messageId: {}, receiverId: {}, type: {}, 错误: {}",
                                messageId, receiverId, type,
                                future.cause() != null ? future.cause().getMessage() : "未知错误");
                    }
                });
            } else {
                log.debug("接收者当前无可用 WebSocket 连接，messageId: {}, receiverId: {}", messageId, receiverId);
            }

            // 4. 发送到Kafka进行持久化
            log.info("系统通知发送到 Kafka 持久化主题，messageId: {}, receiverId: {}, type: {}",
                    messageId, receiverId, type);

            kafkaTemplate.send(CommonConstant.KAFKA_STORE_NOTIFICATION_TOPIC, message).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("系统通知持久化消息发送成功，messageId: {}, receiverId: {}, type: {}",
                            messageId, receiverId, type);
                } else {
                    log.error("系统通知持久化消息发送失败，messageId: {}, receiverId: {}, type: {}, 错误: {}",
                            messageId, receiverId, type, ex.getMessage());
                }
            });


        } catch (Exception e) {
            log.error("处理系统通知消息失败，消息: {}, 错误: {}", message, e.getMessage(), e);
        }
    }
}
