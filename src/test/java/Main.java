import com.github.xy02.nats.Client;
import com.github.xy02.nats.Msg;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.concurrent.TimeUnit;


public class Main {

    public static void main(String[] args) {
        Client client = new Client();
        try {
            Disposable d = client.connect("192.168.8.99");
            client.subscribeMsg("test")
                    .doOnNext(msg -> System.out.println(msg.getSubject() + ":" + new String(msg.getBody())))
//                .take(2)
                    .subscribe(msg -> {
                    }, err -> {
                    }, () -> System.out.println("subscribeMsg onComplete"));
            Msg testMsg = new Msg("test", "hello".getBytes());
            Observable.interval(0, 50, TimeUnit.MICROSECONDS)
                    .flatMapCompletable(x -> client.publish(testMsg))
                    .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                    .subscribe();
            Observable.timer(5, TimeUnit.SECONDS)
                    .subscribe(x -> d.dispose());
            Observable.interval(0, 50, TimeUnit.MICROSECONDS)
                    .flatMapCompletable(x -> client.ping(1, TimeUnit.SECONDS))
                    .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                    .subscribe();
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
