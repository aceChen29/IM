package com.ace.im.user.constants;

/** UserService 使用的 Kafka Topic 与消费组名称。 */
public final class KafkaTopicConstant {

    private KafkaTopicConstant() {
    }

    public static final String TOPIC_SYSTEM_NOTIFICATION = "system-notification-topic";
    public static final String TOPIC_FRIEND_REQUEST_CREATION = "friend-request-creation-topic";
    public static final String TOPIC_FRIEND_REQUEST_EXPIRATION = "friend-request-expiration-topic";

    public static final String GROUP_SYSTEM_NOTIFICATION_CONSUMER = "system-notification-consumer-group";
    public static final String GROUP_FRIEND_REQUEST_ENQUEUER = "friend-request-enqueuer-group";
    public static final String GROUP_FRIEND_REQUEST_EXECUTOR = "friend-request-executor-group";
}
