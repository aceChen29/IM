package com.ace.im.realtime.utils;

import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {
    //a 给b发消息，通过userId找到对应channel
    private static final ConcurrentHashMap<String, Channel> USER_CHANNEL_MAP = new ConcurrentHashMap<String, Channel>();
    //通过channel找到userId
    private static final ConcurrentHashMap<Channel, String> CHANNEL_USER_MAP = new ConcurrentHashMap<Channel, String>();

    public static void addUserChannel(String userId, Channel channel) {
        USER_CHANNEL_MAP.put(userId, channel);
    }

    public static void addChannelUser(String userId, Channel channel) {
        CHANNEL_USER_MAP.put(channel, userId);
    }

    public static void removeChannelUser(Channel channel) {
        CHANNEL_USER_MAP.remove(channel);
    }

    public static void removeUserChannel(String userId) {
        USER_CHANNEL_MAP.remove(userId);
    }

    public static Channel getChannelByUserId(String userId) {
        return USER_CHANNEL_MAP.get(userId);
    }

    public static String getUserIdByChannel(Channel channel) {
        return CHANNEL_USER_MAP.get(channel);
    }

}