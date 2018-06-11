import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class RawSend {
    public static void main(String[] args) {
        try {
            Socket socket = new Socket("127.0.0.1", 4222);
            OutputStream os = new BufferedOutputStream(socket.getOutputStream(), 1024 * 64);
            byte[] BUFFER_CONNECT = "CONNECT {\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"name\":\"\",\"lang\":\"java\",\"version\":\"0.2.3\",\"protocol\":0}\r\n".getBytes();
            os.write(BUFFER_CONNECT);
            os.flush();
            byte[] data = "PUB test3 5\r\nhello\r\n".getBytes();
            Observable.interval(0, 1, TimeUnit.NANOSECONDS)
                    .takeUntil(Observable.timer(10, TimeUnit.SECONDS))
                    .doOnNext(x -> {
                        os.write(data);
                        ++send;
                    })
                    .doOnComplete(() -> System.out.printf("send: %d\n", send))
                    .subscribe();
            Observable.interval(0, 100, TimeUnit.MICROSECONDS)
                    .doOnNext(x -> os.flush())
                    .subscribe();

            Thread.sleep(Long.MAX_VALUE);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static long send = 0;
}
