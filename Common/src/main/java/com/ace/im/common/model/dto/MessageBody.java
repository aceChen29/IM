package com.ace.im.common.model.dto;


import lombok.Data;

@Data
public class MessageBody {


    private String content;


    private Long replyId;


    /** 红包 ID（红包消息专用）。 */
    private String redPacketId;

    /** 红包封面文案（红包消息专用）。 */
    private String redPacketWrapperText;

}