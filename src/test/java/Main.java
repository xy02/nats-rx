import com.github.xy02.nats.Client;
import com.github.xy02.nats.Connection;
import com.github.xy02.nats.IConnection;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        try {

            IConnection nc = new Connection();
            nc.connect()
//                    .doOnNext(t -> System.out.printf("Received a message: %s\n", t))
                    .subscribe();

//            Client nc = new Client("127.0.0.1");
            nc.subscribeMsg("test1")
//                    .doOnNext(msg -> System.out.printf("Received a message: %s\n", new String(msg.getBody())))
//                .take(2)
                    .subscribe(msg -> {
                    }, err -> {
                    }, () -> System.out.println("subscribeMsg onComplete"));
//
//            MSG testMsg = new MSG("test", "hello".getBytes());
//            Observable.interval(0, 1, TimeUnit.NANOSECONDS)
//                    .takeUntil(Observable.timer(10, TimeUnit.SECONDS))
//                    .flatMapCompletable(x -> client.publish(testMsg))
////                    .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
//                    .subscribe();

//            Observable.timer(5, TimeUnit.SECONDS)
//                    .subscribe(x -> client.close());

//            Observable.interval(0, 40, TimeUnit.MICROSECONDS)
//                    .flatMapCompletable(x -> client.ping(1, TimeUnit.SECONDS))
//                    .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
//                    .subscribe();

            Thread.sleep(Long.MAX_VALUE);

//            Socket socket = new Socket("127.0.0.1", 4222);
//            OutputStream os = new BufferedOutputStream(socket.getOutputStream(), 1024 * 64);
//            byte[] BUFFER_CONNECT = "CONNECT {\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"name\":\"\",\"lang\":\"java\",\"version\":\"0.2.3\",\"protocol\":0}\r\n".getBytes();
//            os.write(BUFFER_CONNECT);
//            os.flush();
//            byte[] data = "PUB test 5\r\nhello\r\n".getBytes();
//            Observable.timer(1,TimeUnit.MILLISECONDS).take(1).concatWith(
//            Observable.interval(0, 1, TimeUnit.NANOSECONDS)
//            )
//                    .takeUntil(Observable.timer(10, TimeUnit.SECONDS))
//                    .doOnNext(x -> {
//                        os.write(data);
//                        ++send;
//                    })
//                    .doOnComplete(() -> System.out.printf("send: %d\n", send))
//                    .subscribe();
//            Observable.interval(0, 100, TimeUnit.MICROSECONDS)
//                    .doOnNext(x -> os.flush())
//                    .subscribe();
//
//            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static long send = 0;

}
