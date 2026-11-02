package com.ace.im.offlinedata.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/** 单会话聊天记录搜索请求，使用 search_after 游标分页。 */
@Data
public class MessageSearchRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 必填，会话 ID；当前仅支持搜索一个有权限访问的会话。 */
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    /** 搜索关键词。 */
    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    /** 每页数量，沿用统一分页请求的默认值和最大值。 */
    private Integer pageSize = 20;

    /** 可选，搜索起始时间（包含），支持 Z 或 +08:00 等时区偏移。 */
    private OffsetDateTime startTime;

    /** 可选，搜索结束时间（不包含）；与 startTime 同时提供时必须晚于起始时间。 */
    private OffsetDateTime endTime;

    /** 深分页游标，第一页为空；后续原样传回上一页的 nextCursor。 */
    @Valid
    private MessageSearchCursor cursor;

    public Integer getPageSize() {
        return pageSize == null ? 20 : Math.min(pageSize, 100);
    }
}
