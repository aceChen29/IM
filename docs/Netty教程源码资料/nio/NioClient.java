import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class NioClient {

    public static void main(String[] args) throws IOException {
        try (SocketChannel client = SocketChannel.open(
                new InetSocketAddress("127.0.0.1", 9102))) {
            ByteBuffer message = StandardCharsets.UTF_8.encode("hello NIO");
            while (message.hasRemaining()) {
                client.write(message);
            }
            System.out.println("Message sent");
        }
    }
}
