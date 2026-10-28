package com.ace.im.user.model.vo;

import lombok.Data;

@Data
public class LoginAndRegisterResponse {

    private Long userId;

    private String email;

    private String nickname;

    private String avatar;

    private Integer gender;

    private String description;

    private String accessToken;

    private String refreshToken;

    /**
     * 分配给当前用户的 RealTimeService WebSocket 连接地址。
     * 客户端登录后可用它建立长连接，接收实时消息；连接时仍需携带身份凭证。
     * 当前登录和注册实现尚未给此字段赋值，返回时可能为 null。
     */
    private String nettyUri;
    //记录上次断开ws连接 离线时间
    private Long offlineTime;
}
