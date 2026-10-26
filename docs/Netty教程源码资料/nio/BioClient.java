import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class BioClient {

    public static void main(String[] args) {
        Thread tom = new Thread(() -> {
            try {
                sendHello();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "tom");

        Thread jerry = new Thread(() -> {
            try {
                sendHello();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "jerry");

        tom.start();
        jerry.start();
    }

    private static void sendHello() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 8080));
            OutputStream outputStream = socket.getOutputStream();
            for (int i = 0; i < 10; i++) {
                outputStream.write((Thread.currentThread().getName() + "hello " + i)
                        .getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            Thread.sleep(10000);
        }
    }
}
