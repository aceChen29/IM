package com.ace.im.realtime.websocket;



import com.ace.im.common.constant.CommonConstant;
import com.ace.im.common.utils.JwtUtil;
import com.ace.im.realtime.utils.ChannelManager;
import io.jsonwebtoken.Claims;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

//用于身份校验
//ChannelInboundHandler 是 Netty 的接口，定义了收到消息、连接建立、连接断开等入站事件的处理方法。

//ChannelInboundHandlerAdapter 是这个接口的现成实现类。它已经实现了这些方法，默认把事件传给 Pipeline 中的下一个处理器。
//SimpleChannelInboundHandler<I> 继承 ChannelInboundHandlerAdapter，是抽象类；channelRead0 只处理匹配类型的消息，并在处理后默认自动释放该消息。
//选择原则：处理后无需传递，用 SimpleChannelInboundHandler；需要自己控制转发和消息生命周期，用 ChannelInboundHandlerAdapter。
@RequiredArgsConstructor
public class WebSocketAuthHandler extends ChannelInboundHandlerAdapter {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)  {
        if (msg instanceof FullHttpRequest request){
            String authHeader = request.headers().get("Authorization");
            if (authHeader == null || authHeader.isEmpty()) {
                ctx.close();
                return;
            }
            try {
                Claims claims = JwtUtil.parse(authHeader);
                if (claims == null) {
                    ctx.close();
                    return;
                }

                String userId = claims.getSubject();
                if (userId == null || userId.isEmpty()) {
                    ctx.close();
                    return;
                }

                String redisAccessToken = stringRedisTemplate.opsForValue().get(CommonConstant.ACCESS_TOKEN_PREFIX + userId);
                if (StringUtils.isEmpty(redisAccessToken) || !authHeader.equals(redisAccessToken)) {
                    ctx.close();
                    return;
                }

                // 3. 绑定用户与 channel
                ChannelManager.addUserChannel(userId, ctx.channel());
                ChannelManager.addChannelUser(userId, ctx.channel());
                ctx.fireChannelRead(msg);
            } catch (Exception e) {
                // 记录日志
                ctx.close();
            }

        } else {
            ctx.fireChannelRead(msg);
        }
    }
}
