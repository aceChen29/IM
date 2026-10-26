package com.ace.im.realtime.websocket;

import cn.hutool.json.JSONUtil;
import com.ace.im.common.constant.CommonConstant;
import com.ace.im.common.constant.WebSocketConstant;
import com.ace.im.common.model.dto.MessageRequest;
import com.ace.im.realtime.utils.ChannelManager;
import com.ace.im.realtime.utils.SnowflakeDynamicUtil;
import io.micrometer.common.util.StringUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Date;
//SimpleChannelInboundHandler 当前这条入站消息在这个 Handler 处理完后，不再传给后面的入站 Handler

@Slf4j
@RequiredArgsConstructor
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final KafkaTemplate<String, String> kafkaTemplate;
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) throws Exception {
        //序列化之后的msg
        String msg = textWebSocketFrame.text();
        Channel channel = ctx.channel();
        try {
            //消息是心跳消息
            if(msg.equals(WebSocketConstant.HEARTBEAT_PING)){
                if(channel.isActive()){
                    log.debug("Received heartbeat ping from {}", channel.id());
                    channel.writeAndFlush(new TextWebSocketFrame(WebSocketConstant.HEARTBEAT_PONG));
                }
            }
            //消息是业务消息
            else{
                if(channel.isActive()){
                    sendMessageToKafka(msg);
                }
                else {
                    log.warn("Channel {} inactive, skip message: {}", channel.id(), msg);
                }

            }
        } catch (Exception e) {
            log.error("Error handling message from {}: {}", channel.id(), msg, e);
            cleanChannel(channel);
            ctx.close();
        }


        //System.out.println(msg);
    }
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {

        // 处理心跳
        if (evt instanceof IdleStateEvent event) {
            switch (event.state()) {
                case READER_IDLE:
                    log.error("读空闲超时");
                    cleanChannel(ctx.channel());
                    ctx.close();
                    break;
                case WRITER_IDLE:
                    log.error("写空闲超时");
                case ALL_IDLE:
                    log.error("读写空闲超时");
            }
        }
    }
    private void sendMessageToKafka(String msg) {
        MessageRequest messageRequest = JSONUtil.toBean(msg, MessageRequest.class);
        messageRequest.setMessageId(SnowflakeDynamicUtil.nextId());//避免realtimeservice多实例时消息id冲突
        messageRequest.setCreatedTime(new Date());//创建一个表示当前时间的 Date 对象
        //topic用于后续持久到数据库
        //实际只需要写
        //kafkaTemplate.send(CommonConstant.KAFKA_MESSAGE_TOPIC_STORE, JSONUtil.toJsonStr(msg));
        //便于调试
        kafkaTemplate.send(CommonConstant.KAFKA_MESSAGE_TOPIC_STORE, JSONUtil.toJsonStr(messageRequest))
                .whenComplete((success, failure) -> {
                    if (failure != null) {
                        // 生产者生产失败
                        System.err.println("生产者生产失败: " + failure.getMessage());
                        // 记录日志、告警、补偿等
                    } else {
                        // 生产者生产成功
                        System.out.println("生产者生产成功，offset: " + success.getRecordMetadata().offset());
                    }
        });

        //topic 用于消息推送 将sessionId作为key，会根据key的哈希值进入topic里的同一个分区
        kafkaTemplate.send(CommonConstant.KAFKA_MESSAGE_TOPIC_PUSH, messageRequest.getSessionId().toString(), JSONUtil.toJsonStr(messageRequest))
                .whenComplete((success, failure) -> {
                    if (failure != null) {
                        // 生产者生产失败
                        System.err.println("生产者生产失败: " + failure.getMessage());
                        // 记录日志、告警、补偿等
                    } else {
                        // 生产者生产成功
                        System.out.println("生产者生产成功，offset: " + success.getRecordMetadata().offset());
                    }
                });
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        System.out.println(ctx + "channel 可以使用了");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanChannel(ctx.channel());
        super.channelInactive(ctx);
        System.out.println(ctx + "channel 客户端主动断开或者服务器关闭连接");
    }
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        System.out.println("handler added");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanChannel(ctx.channel());
        super.handlerRemoved(ctx);

        System.out.println("handler removed");
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception caught in channel pipeline", cause);
        super.exceptionCaught(ctx, cause);
    }

    //移除管道时需要清除channel和userId的映射关系
    public void cleanChannel(Channel channel){
        if(channel == null )return;
        System.out.println("clearChannel: " + channel.id());
        String userId = ChannelManager.getUserIdByChannel(channel);
        try {
            if(StringUtils.isNotBlank(userId))
                ChannelManager.removeUserChannel(userId);
            ChannelManager.removeChannelUser(channel);
        } catch (Exception e) {
            log.error("clearChannel failed for channel: {}, userId: {}", channel.id(), userId, e);
        } finally {
            if(channel.isActive())
                channel.close();
        }

    }
}
