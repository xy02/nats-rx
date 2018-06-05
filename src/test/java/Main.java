import com.github.xy02.nats.Client;
import com.github.xy02.nats.Msg;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.concurrent.TimeUnit;


public class Main {
    public static void main(String[] args) {
        Msg testMsg = new Msg("test", "hello".getBytes());
        Client client = new Client("192.168.8.99");
        Disposable d = client.connect().subscribe();
        client.subscribeMsg("test")
                .doOnNext(msg -> System.out.println(msg.getSubject() + ":" + new String(msg.getBody())))
                .subscribe(msg -> {
                }, err -> {
                }, () -> System.out.println("subscribeMsg onComplete"));
        Observable.interval(0, 50, TimeUnit.MICROSECONDS)
                .flatMapCompletable(x -> client.publish(testMsg))
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .subscribe();
//        Observable.interval(0,50, TimeUnit.MICROSECONDS)
//                .flatMapCompletable(x -> client.ping(1, TimeUnit.SECONDS))
//                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
//                .subscribe();
        Observable.timer(10, TimeUnit.SECONDS)
                .subscribe(x -> d.dispose());
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception ex) {
        }
    }
}
