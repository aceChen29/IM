package com.ace.im.offlinedata.service;


import com.ace.im.common.model.dto.MessageRequest;
import com.ace.im.common.model.entity.Message;
import com.ace.im.common.model.vo.MessageResponse;
import com.ace.im.offlinedata.model.dto.HistoryMessageRequest;
import com.ace.im.offlinedata.model.dto.OfflineMessageRequest;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
* @author cy
* @description 针对表【message】的数据库操作Service
* @createDate 2026-10-27 22:36:43
*/
public interface MessageService extends IService<Message> {

    void saveMessageToMySQL(MessageRequest messageRequest);

    Map<Long, List<MessageResponse>> getOfflineMessages(OfflineMessageRequest request);

    List<MessageResponse> getHistoryMessages(HistoryMessageRequest request);
}
