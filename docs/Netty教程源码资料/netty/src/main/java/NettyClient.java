import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.util.concurrent.TimeUnit;

public class NettyClient {
    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup());
        bootstrap.channel(NioSocketChannel.class) //指定可实例化的 NIO 客户端 Channel 类型
                //初始化channel
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new StringEncoder())
                                .addLast(new StringDecoder())
                                .addLast(new SimpleChannelInboundHandler<String>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                                        System.out.println("来自服务器的" + msg);
                                    }
                                });
                    }
                });
        ChannelFuture connectFuture = bootstrap.connect("localhost", 8080);
        connectFuture.addListener(future -> {
            if(future.isSuccess()){
                System.out.println("客户端成功连接端口8080服务器");
                //拿到channel后往channel中写数据，经过出站处理器string encoder序列化成数组
                EventLoop eventExecutors = connectFuture.channel().eventLoop();//eventLoop可以执行定时任务
                eventExecutors.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        connectFuture.channel().writeAndFlush("hello" + System.currentTimeMillis());
                    }
                },0, 1, TimeUnit.SECONDS);
            }
            else
                System.out.println("客户端连接8080端口失败");

        });
    }
}
