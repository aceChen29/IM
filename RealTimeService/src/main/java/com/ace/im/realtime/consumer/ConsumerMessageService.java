package com.ace.im.realtime.consumer;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;

import com.ace.im.common.common.BaseResponse;
import com.ace.im.common.constant.SessionTypeConstant;
import com.ace.im.common.model.dto.MessageRequest;
import com.ace.im.common.model.vo.MessageResponse;
import com.ace.im.common.utils.FormatDateUtil;
import com.ace.im.realtime.utils.ChannelManager;
import com.ace.im.user.api.client.UserServiceClient;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
public class ConsumerMessageService {
/*
发送方通过自己的 WebSocket 连接发消息
→ Netty 的 WebSocketHandler 收到消息并写入 Kafka 的 message-topic
→ ConsumerMessageService 消费这条消息
→ 分别找到发送方和接收方的 Channel
→ 通过两个 Channel 把消息推送回各自的客户端
* */
    private final UserServiceClient userServiceClient;

    // Spring Kafka 为这个方法创建消费者并订阅 message-topic；有新消息时，框架把消息内容传入 message 参数并调用此方法。
    // groupId 是消费组 ID：同一组的多个消费者共同消费该 topic，而不是每个消费者都收到一份。
    // 每个实例从配置读取自己的消费组 ID；多实例使用不同组，使各实例都能收到消息并检查本地 Channel。
    @KafkaListener(topics = "message-topic", groupId = "${im.kafka.push-group-id}")
    public void consume(String message) {
        System.out.println("收到序列化消息：" + message);
        // Kafka 中存的是 JSON 字符串，先还原成业务对象，再根据会话类型选择推送方式。
        MessageRequest messageRequest = JSONUtil.toBean(message, MessageRequest.class);
        System.out.println("反序列化消息为：" + messageRequest);
        if (messageRequest.getSessionType() == SessionTypeConstant.SIGNAL_TYPE) {
            signalMessage(messageRequest);
        } else if (messageRequest.getSessionType() == SessionTypeConstant.GROUP_TYPE) {
            groupMessage(messageRequest);
        }
    }

    // 单聊：把服务端处理后的消息分别推给发送方和接收方；发送方可据此更新自己发出的消息。
    public void signalMessage(MessageRequest messageRequest) {
        MessageResponse messageResponse = new MessageResponse();
        BeanUtil.copyProperties(messageRequest, messageResponse);
        messageResponse.setCreatedTime(FormatDateUtil.formatDate(messageRequest.getCreatedTime()));
        pushMessageToUser(messageResponse, messageRequest.getSenderId());// 找发送方的 Channel
        pushMessageToUser(messageResponse, messageRequest.getReceiverId()); // 找接收方的 Channel

    }


    public void groupMessage(MessageRequest messageRequest) {
        Long sessionId = messageRequest.getSessionId();
        //根据sessionId得到对应群聊里的userId openFeign
        List<Long> userIds = userServiceClient.getUserIdBySessionId(sessionId);

        MessageResponse messageResponse = new MessageResponse();
        BeanUtil.copyProperties(messageRequest, messageResponse);
        messageResponse.setCreatedTime(FormatDateUtil.formatDate(messageRequest.getCreatedTime()));
        //循环遍历userIds 将消息推送到对应channel
        for(Long userId : userIds){
            pushMessageToUser(messageResponse, userId);
        }
    }



    // 根据用户 ID 查找本服务实例中已有的 WebSocket 连接，再把消息写入该连接。
    public void pushMessageToUser(MessageResponse messageResponse, Long receiverId) {
        Channel channel = ChannelManager.getChannelByUserId(receiverId.toString());
        if (channel != null) {
            TextWebSocketFrame frame = new TextWebSocketFrame(JSONUtil.toJsonStr(messageResponse));
            // 写入是异步的；监听器记录本次网络写入成功或失败，不代表客户端已读消息。
            channel.writeAndFlush(frame).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("消息发送成功: {}", messageResponse);
                } else {
                    log.info("消息发送失败: {}", future.cause() != null ? future.cause().getMessage() : "未知错误");
                }
            });
        } else {
            log.info("channel 不存在");
        }
    }

}
