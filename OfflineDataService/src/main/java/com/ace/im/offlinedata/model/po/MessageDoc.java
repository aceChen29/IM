package com.ace.im.offlinedata.model.po;

import lombok.Data;

import java.util.Date;

/** message 索引中的消息文档，字段与 MessageIndexCreator 的 mapping 对应。 */
@Data
public class MessageDoc {

    /** 消息 ID，同时用作 ES 文档的 _id。 */
    private String messageId;

    /** 发送者 ID。 */
    private String senderId;

    /** 会话 ID，用于限定搜索范围。 */
    private String sessionId;

    /** 消息类型：0 文本，1 图片，2 表情包，3 红包；第一版只索引文本。 */
    private Integer type;

    /** 消息原文，由 ES 进行 IK 分词；高亮内容在查询时生成。 */
    private String content;

    /** 被引用的消息 ID，没有引用时为 null。 */
    private String replyId;

    /** 会话类型：0 单聊，1 群聊。 */
    private Integer sessionType;

    /** 数据库消息创建时间的毫秒时间戳，对应 ES date 的 epoch_millis 格式。 */
    private Date createdTime;
    private Date updatedTime;
}
