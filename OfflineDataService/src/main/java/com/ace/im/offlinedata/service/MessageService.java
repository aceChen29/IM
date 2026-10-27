package com.ace.im.offlinedata.service;


import com.ace.im.common.model.dto.MessageRequest;
import com.ace.im.common.model.entity.Message;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author cy
* @description 针对表【message】的数据库操作Service
* @createDate 2026-10-27 22:36:43
*/
public interface MessageService extends IService<Message> {

    void saveMessageToMySQL(MessageRequest messageRequest);
}
