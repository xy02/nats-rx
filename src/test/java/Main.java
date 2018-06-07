import com.github.xy02.nats.Client;
import com.github.xy02.nats.Msg;
import io.reactivex.Observable;

import java.util.concurrent.TimeUnit;


public class Main {
    public static void main(String[] args) {
        Msg testMsg = new Msg("test", "hello".getBytes());
        Client client = new Client("192.168.8.99");
        client.subscribeMsg("test")
                .doOnNext(msg -> System.out.println(msg.getSubject() + ":" + new String(msg.getBody())))
//                .take(2)
                .subscribe(msg -> {
                }, err -> {
                }, () -> System.out.println("subscribeMsg onComplete"));
        Observable.interval(0, 50, TimeUnit.MICROSECONDS)
                .flatMapCompletable(x -> client.publish(testMsg))
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .subscribe();
        Observable.timer(5, TimeUnit.SECONDS)
                .subscribe(x -> client.close());
//        Observable.interval(0,50, TimeUnit.MICROSECONDS)
//                .flatMapCompletable(x -> client.ping(1, TimeUnit.SECONDS))
//                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
//                .subscribe();

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception ex) {
        }
    }

    public static Observable<Integer> test() {
        return Observable.just(2).doOnNext(System.out::println);
    }
}
