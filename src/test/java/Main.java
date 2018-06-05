import com.github.xy02.nats.Client;
import io.reactivex.Observable;

import java.util.concurrent.TimeUnit;


public class Main {
    public static void main(String[] args) {
        Client.connect("192.168.8.99")
                .doOnSuccess(client -> System.out.println(client))
                .toObservable()
//                .flatMap(client -> Observable.interval(50, TimeUnit.MICROSECONDS).map(x -> client))
//                .flatMapCompletable(client -> client.ping(1, TimeUnit.SECONDS))
//                .doOnComplete(() -> System.out.println("doOnComplete"))
                .flatMap(client -> client.subscribe("test"))
                .doOnNext(msg -> System.out.println(msg.getSubject()+":"+new String(msg.getBody())))
                .subscribe();
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception ex) {
        }
    }
}
