package com.ace.im.offlinedata.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 时间倒序搜索的分页位置，取上一页最后一条命中的 ES sort 值。 */
@Data
public class MessageSearchCursor implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息创建时间的毫秒时间戳，对应第一排序字段。 */
    @NotNull(message = "游标创建时间不能为空")
    @Positive(message = "游标创建时间必须大于0")
    private Long createdTime;

    /** 消息 ID，对应第二排序字段，用于区分创建时间相同的消息。 */
    @NotBlank(message = "游标消息ID不能为空")
    private String messageId;
}
