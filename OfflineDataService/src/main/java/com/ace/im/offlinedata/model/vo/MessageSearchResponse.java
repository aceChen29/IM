package com.ace.im.offlinedata.model.vo;

import com.ace.im.offlinedata.model.dto.MessageSearchCursor;
import lombok.Data;
import java.util.List;

@Data
public class MessageSearchResponse {
    private List<MessageSearchVO> records;
    private boolean hasMore;
    private MessageSearchCursor nextCursor;
}