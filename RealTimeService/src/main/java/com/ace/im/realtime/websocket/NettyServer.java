package com.ace.im.realtime.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.NettyRuntime;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
@RequiredArgsConstructor
@Component
public class NettyServer {
    private final int port = 9101;
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    //worker线程数为cpu核数*2

    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup(NettyRuntime.availableProcessors() * 2);
    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;//分别是 消息键（key）的类型和消息内容（value）的类型

    /*
    @Configuration 使 NettyService 成为 Spring 管理的 Bean。
    Spring 创建它时，bossGroup、workerGroup 会随对象一起创建；
    但 Spring 不会因为类里有 run() 方法就自动调用它，也不知道退出时该关闭这两个线程组。因此这里写：

    - @PostConstruct：Bean 完成创建和依赖注入后，调用 start()，启动 Netty 并绑定端口。
    - @PreDestroy：Spring 容器关闭时，调用 destroy()，释放 Netty 线程组。

    其他 Controller、Service 通常只需要让 Spring 注入依赖、在收到请求时调用方法，没有“创建后立即启动”和“关闭前释放”的工作，所以不用这两个注解。你贴的 NettyService 没有注入任何字段；
    这里使用注解的直接原因是管理 Netty 服务器的启动与关闭，而不是完成自动装配。
    * */
    @PostConstruct
    public void start() throws InterruptedException {
        run();
    }
    public void run() throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup);
        serverBootstrap.channel(NioServerSocketChannel.class);
        serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline()
                        //readerIdleTimeSeconds: 读超时. 即当在指定的时间间隔内没有从 Channel 读取到数据时, 会触发一个 READER_IDLE 的 IdleStateEvent 事件.
                        //writerIdleTimeSeconds: 写超时. 即当在指定的时间间隔内没有数据写入到 Channel 时, 会触发一个 WRITER_IDLE 的 IdleStateEvent 事件.
                        //allIdleTimeSeconds: 读/写超时. 即当在指定的时间间隔内没有读或写操作时, 会触发一个 ALL_IDLE 的 IdleStateEvent 事件.

                        // 双向处理器：可监测入站读和出站写；这里只启用读空闲检测，60 秒未收到数据时触发 READER_IDLE 事件，两个 0 表示不检测写空闲和读写空闲。
                        //空闲状态处理器 ：关键是放在处理空闲事件的 WebSocketHandler 前面。当前放法还意味着：HTTP 握手期间收到的数据也算作“有读取”，60 秒计时关注的是整个连接的读取活动，而不只是业务文本消息
                        .addLast(new IdleStateHandler(60, 0, 0))
                        // 双向：入站把 HTTP 字节解码成请求/内容；出站把 HTTP 响应编码成字节。
                        .addLast(new HttpServerCodec())
                        // 入站：把分段的 HTTP 请求聚合成 FullHttpRequest；最大聚合长度为 65536 字节。
                        // 这个值限制 HTTP 消息，不是 WebSocket 消息帧的大小。
                        .addLast(new HttpObjectAggregator(65536))
                        //校验请求头， 用户鉴权通过将userId绑定到channel 2个map双向绑定
                        .addLast(new WebSocketAuthHandler(stringRedisTemplate))
                        // 双向：处理 /ws/netty 的 WebSocket 升级握手和 Close、Ping、Pong 等控制帧；
                        // 普通文本/二进制帧继续交给后面的业务处理器，帧的编解码由协议处理器配置。
                        .addLast(new WebSocketServerProtocolHandler("/ws/netty"))
                        // 入站：自定义处理器接收 TextWebSocketFrame，当前实现会打印帧里的文本。
                        .addLast(new WebSocketHandler(kafkaTemplate));

            }
        });
        serverBootstrap.bind(port).sync();

    }
    @PreDestroy
    public void destroy() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
