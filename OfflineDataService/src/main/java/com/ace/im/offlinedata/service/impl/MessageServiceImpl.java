package com.ace.im.offlinedata.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ace.im.common.common.ErrorCode;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.common.model.dto.MessageRequest;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ace.im.common.model.entity.Message;
import com.ace.im.offlinedata.service.MessageService;
import com.ace.im.offlinedata.mapper.MessageMapper;
import org.springframework.stereotype.Service;

/**
* @author cy
* @description 针对表【message】的数据库操作Service实现
* @createDate 2026-10-27 22:36:43
*/
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
    implements MessageService{

    @Override
    public void saveMessageToMySQL(MessageRequest messageRequest) {
        Message message = new Message();
        BeanUtil.copyProperties(messageRequest, message);
        message.setContent(messageRequest.getBody().getContent());
        message.setReplyId(messageRequest.getBody().getReplyId());
        if(!save(message))
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
    }
}




