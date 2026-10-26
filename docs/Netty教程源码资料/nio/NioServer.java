import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class NioServer {

    public static void main(String[] args) throws IOException {
        //设置选择器去监听Server
        Selector selector = Selector.open();
        ServerSocketChannel ssc = ServerSocketChannel.open();//服务器通道打开
        ssc.configureBlocking(false);//设置为非阻塞的
        ssc.bind(new InetSocketAddress(8080)); //绑定端口号

        //向serversocketchannel注册一个selector， 监听accept事件
        ssc.register(selector, SelectionKey.OP_ACCEPT);
        while(true){
            selector.select();// 监听ssc上的accept事件，阻塞直至事件触发返回
            //selector.selectedKeys(); //拿到当前监听的所有channel 把channel注册给selector后每个channel对应一个selectorkey
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while(iterator.hasNext()){
                SelectionKey key = iterator.next();
                //selector 并不知道事件是否处理完成，所以每次拿到key之后要把key从SelectionKey中移除
                iterator.remove();
                //SelectableChannel channel = key.channel(); accept事件时等价于ssc == channel
                if(key.isAcceptable()){
                    ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                    SocketChannel client = serverSocketChannel.accept();//accept为服务器接受客户端连接 拿到客户端通道
                    client.configureBlocking(false);//客户端设置为非阻塞
                    client.register(selector, SelectionKey.OP_READ); //把客户端通道中的读事件注册到选择器上
                    System.out.println("客户端已连接" + client.getRemoteAddress());
                }
                if(key.isReadable()){
                    SocketChannel channel = (SocketChannel) key.channel();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                    int length = channel.read(byteBuffer);
                    if(length == -1) {
                        System.out.println("客户端关闭连接" + channel.getRemoteAddress());
                        //length == -1 客户端已关闭
                        channel.close();
                    }else{
                        //客户端往里写，此时切换成读取状态
                        byteBuffer.flip();
                        byte[] buffer = new byte[byteBuffer.remaining()];
                        byteBuffer.get(buffer);
                        String message = new String(buffer);
                        System.out.println(message);
                    }
                }
            }
        }
    }
}
