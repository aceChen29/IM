package com.ace.im.offlinedata.consumer;

import cn.hutool.json.JSONUtil;
import com.ace.im.common.model.dto.MessageRequest;
import com.ace.im.offlinedata.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
/*
@Service 让 Spring 管理这个类
→ Spring 发现 @KafkaListener
→ 创建订阅 store-topic 的消费者
→ 收到消息后调用这个方法，并把消息内容传给 String message

groupId = "infinite-chat-store-group" 指定它属于哪个消费组；方法上的这个值会用于此监听器。
* */
@Service
@RequiredArgsConstructor
public class ConsumerOfflineService {
    private final MessageService messageService;
    @KafkaListener(topics = "store-topic", groupId = "infinite-chat-store-group")
    public void consume(String message){
        System.out.println("Consumed message store: " + message);
        MessageRequest messageRequest = JSONUtil.toBean(message, MessageRequest.class);
        messageService.saveMessageToMySQL(messageRequest);
        System.out.println(messageRequest);

    }
}
