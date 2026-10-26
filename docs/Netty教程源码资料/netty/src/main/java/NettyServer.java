import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NettyServer {

    public static void main(String[] args) {
        //模拟消息持久化存储
        Map<Channel, List<String>> db = new ConcurrentHashMap<>();
        // 在这里编写 Netty 服务端示例。
        //启动类
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        //添加boss worker
        serverBootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        //指定channel类型
        serverBootstrap.channel(NioServerSocketChannel.class);
        //对于每一个客户端连接都会有一个socketchannel
        // 每个socketchannel中有自己的pipeline pipeline中有入站出站处理器进行相关处理
        serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            //初始化这个channel， 对socketChannel进行初始化
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                //入站服务器解码handler
                ch.pipeline().addLast(new StringDecoder())
                        //出站编码处理器
                        .addLast(new StringEncoder())

                        //反序列化得到String后对String进行处理 入站
                        .addLast(new SimpleChannelInboundHandler<String>() {
                            //该函数真正读取了消息，将消息封装到msg
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                                System.out.println("来自客户端的：" + msg);
                                String message = msg + "world";
                                /*
                                 * Pipeline 可理解为双向链表，例如：
                                 * Head -> 入站1 -> 出站2 -> 出站3 -> 入站4(当前 ctx) -> 出站5 -> 出站6 -> Tail
                                 * 入站事件从 Head 向 Tail 传播，只经过入站1、入站4。
                                 * 出站事件从起点向 Head 传播，只经过途中遇到的出站处理器。
                                 *
                                 * ctx.channel().writeAndFlush(message)：从 Tail 开始向 Head 查找，
                                 *   依次经过出站6、5、3、2，最后由 Head 写入网络。
                                 * ctx.writeAndFlush(message)：从当前 ctx 的前一个节点开始向 Head 查找，
                                 *   只经过出站3、2；不会经过位于当前 ctx 后面的出站5、6。
                                 * 注意：出站不会先正向走到 Tail 再折返。
                                 *
                                 * 本例实际顺序是 Head -> StringDecoder(入站) -> StringEncoder(出站)
                                 * -> 当前入站处理器 -> Tail，所以这两种写法都会经过 StringEncoder。
                                 */
                                // ctx.writeAndFlush(message); // 从当前 ctx 向 Head 查找出站处理器
                                ctx.channel().writeAndFlush(message); // 从 Tail 向 Head 查找出站处理器
                                ctx.fireChannelRead(msg); // 继续交给后面的入站处理器保存消息 如果不写不会将消息传给下一个入站处理器
                            }
                        })
                        .addLast(new SimpleChannelInboundHandler<String>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {

                                //- 如果 db 中已有当前 Channel 对应的列表，就直接返回那个列表。
                                //- 如果没有，就执行 channel -> new ArrayList<>() 创建列表，存入 db，再返回它。
                                //List<String> createList(Channel channel) {
                                //    return new ArrayList<String>();
                                //}
                                List<String> messageList = db.computeIfAbsent(ctx.channel(), channel -> new ArrayList<>());
                                messageList.add(msg);
                            }

                            //监听器即为handler本体，可以监听注册，activate等事件
                            @Override
                            public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                                System.out.println(ctx + "channel 注册了");
                            }

                            @Override
                            public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                                System.out.println(ctx + "channel 解除注册了");
                            }

                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                System.out.println(ctx + "channel 可以使用了");
                            }


                            @Override
                            //对应的连接已经关闭，例如客户端主动断开，或服务端关闭了连接
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                List<String> messageList = db.get(ctx.channel());
                                System.out.println(messageList);
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                super.exceptionCaught(ctx, cause);
                            }
                        });
            }
        });

        //netty中的绑定 读写，等事件都是异步的，用channelfuture来接收异步返回值
        ChannelFuture bindFuture = serverBootstrap.bind(8080);
        bindFuture.addListener(future -> {
            if(future.isSuccess())
                System.out.println("服务器成功监听端口" + 8080);
            else
                System.out.println("监听端口失败");
        });

    }

    //2个simpleChannelInbound处理器还可以进一步封装成一个类，简化代码
}
