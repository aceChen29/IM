package com.ace.im.common.constant;

/** 聊天消息与系统通知的类型编码。 */
public final class MessageTypeConstant {

    private MessageTypeConstant() {
    }

    public static final int TEXT_MESSAGE = 0;
    public static final int IMAGE_MESSAGE = 1;
    public static final int EMOJI_MESSAGE = 2;
    public static final int RED_PACKET_MESSAGE = 3;

    public static final int TYPE_SYSTEM_NEW_APPLY = 101;
    public static final int TYPE_SYSTEM_NEW_SESSION = 102;
    public static final int TYPE_SYSTEM_NEW_GROUP_SESSION = 103;
    public static final int TYPE_SYSTEM_GROUP_KICK = 104;

    public static final int CHAT_MESSAGE_MIN = 0;
    public static final int CHAT_MESSAGE_MAX = 99;
    public static final int SYSTEM_NOTIFICATION_MIN = 100;
    public static final int SYSTEM_NOTIFICATION_MAX = 199;

    public static boolean isSystemNotification(int type) {
        return type >= SYSTEM_NOTIFICATION_MIN && type <= SYSTEM_NOTIFICATION_MAX;
    }

    public static boolean isChatMessage(int type) {
        return type >= CHAT_MESSAGE_MIN && type <= CHAT_MESSAGE_MAX;
    }
}
