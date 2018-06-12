import com.github.xy02.nats.Connection;
import com.github.xy02.nats.MSG;
import io.reactivex.Observable;

import java.util.concurrent.TimeUnit;

public class Main {

    public long read = 0;

    public long length = 0;

    public void test(String subject) {
        Connection nc = new Connection();

        MSG testMsg = new MSG(subject, "hello".getBytes());
        Observable.interval(0, 1, TimeUnit.NANOSECONDS)
//                .takeUntil(Observable.timer(10, TimeUnit.SECONDS))
                .doOnNext(x -> nc.publish(testMsg))
                .doOnComplete(() -> System.out.printf("read: %d\n", read))
//                    .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .subscribe();

        nc.subscribeMsg(subject)
//                .takeUntil(Observable.timer(20, TimeUnit.SECONDS))  **一用takeUtil，natsCPU翻倍，数据传输变慢25% WTF
                .doOnComplete(() -> System.out.printf("read: %d\n", read))
                .doOnNext(msg -> read++)
//                .doOnNext(msg -> length += msg.getBody().length)
//                .doOnNext(msg -> System.out.printf("Received a msg: %s, i:%d\n", new String(msg.getBody()),read))
                .subscribe(msg -> {
                }, err -> {
                }, () -> System.out.println("subscribeMsg onComplete"));

        Observable<String> ping = Observable
                .interval(10, TimeUnit.SECONDS)
                .flatMapSingle(l->nc.ping(TimeUnit.MICROSECONDS).map(t->"ping μs:"+t)
                        .doOnSuccess(System.out::println))
                .retry();
        nc.connect()
//                .doOnError(Throwable::printStackTrace)
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
//                .doOnNext(t -> System.out.printf("Received a message: %s\n", t))
                .mergeWith(ping)
                .subscribe();

        Observable.interval(1, TimeUnit.SECONDS).subscribe(x -> System.out.printf("%d sec read: %d, body length: %d\n", x + 1, read, length));
    }


    public static void main(String[] args) {
        try {

            Main test = new Main();
            test.test("sub1");
//            new Main().test("sub2");

            Thread.sleep(Long.MAX_VALUE);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
