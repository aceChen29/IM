package com.ace.im.offlinedata.consumer;

import cn.hutool.json.JSONUtil;
import com.ace.im.common.common.ErrorCode;
import com.ace.im.common.constant.CommonConstant;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.common.model.dto.MessageRequest;
import com.ace.im.offlinedata.model.po.MessageDoc;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class MessageIndexConsumer {
    private final RestHighLevelClient client;
    @KafkaListener(topics = CommonConstant.INDEX_TOPIC, groupId = CommonConstant.INDEX_GROUP)
    public void consume(String message) throws IOException {
        System.out.println("Consumed message index: " + message);
        MessageDoc doc = JSONUtil.toBean(message, MessageDoc.class);
        if (doc.getMessageId() == null || doc.getSessionId() == null
                || doc.getSenderId() == null || doc.getCreatedTime() == null
                || doc.getContent() == null || !Integer.valueOf(0).equals(doc.getType())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"索引事件字段缺失或不是文本消息");
        }
        //先只做文本消息的es
        if (!Integer.valueOf(0).equals(doc.getType())) {
            return;
        }
        IndexRequest request = new IndexRequest(CommonConstant.INDEX_NAME).id(doc.getMessageId());
        request.source(JSONUtil.toJsonStr(doc), XContentType.JSON);
        client.index(request, RequestOptions.DEFAULT);
    }
}
