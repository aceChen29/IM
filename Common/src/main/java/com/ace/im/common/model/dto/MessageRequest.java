package com.ace.im.common.model.dto;


import lombok.Data;

import java.util.Date;
//消息请求
@Data
public class MessageRequest {

    /** 消息所属的会话 ID。 */
    private Long sessionId;

    /** 接收者的用户 ID；单聊时用于确定消息要发给谁。 */
    private Long receiverId;

    /** 发送者的用户 ID。 */
    private Long senderId;

    /** 消息类型：0 文本，1 图片，2 表情包，3 红包。 */
    private Integer type;

    /** 会话类型：0 单聊，1 群聊。 */
    private Integer sessionType;

    /** 消息创建时间。 */
    private Date createdTime;

    /** 服务端生成的消息 ID。 */
    private Long messageId;

    /** 客户端生成的消息 ID，用于关联客户端消息与服务端返回的消息。 */
    private String clientMessageId;

}
