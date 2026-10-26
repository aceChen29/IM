package com.ace.im.common.model.vo;


import lombok.Data;

@Data
public class MessageResponse {

    /** 消息所属的会话 ID。 */
    private Long sessionId;

    /** 发送者的用户 ID。 */
    private Long senderId;

    /** 消息类型：0 文本，1 图片，2 表情包，3 红包。 */
    private Integer type;

    /** 会话类型：0 单聊，1 群聊。 */
    private Integer sessionType;

    /** 格式化后的消息创建时间。 */
    private String createdTime;

    /** 服务端生成的消息 ID。 */
    private Long messageId;

    /** 客户端生成的消息 ID，用于关联客户端消息与服务端返回的消息。 */
    private String clientMessageId;

    /** 发送者昵称。 */
    private String nickname;

    /** 发送者头像地址。 */
    private String avatar;

    /** 发送者的角色标识；具体取值由业务场景定义。 */
    private Integer role;
}
