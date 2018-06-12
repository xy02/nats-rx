import com.github.xy02.nats.Connection;
import com.github.xy02.nats.MSG;
import io.reactivex.Observable;

import java.util.concurrent.TimeUnit;

public class Main {

    public long read = 0;

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
//                .takeUntil(Observable.timer(20, TimeUnit.SECONDS))
                .doOnNext(msg -> read++)
                .doOnComplete(() -> System.out.printf("read: %d\n", read))
//                .doOnNext(msg -> System.out.printf("Received a msg: %s, i:%d\n", new String(msg.getBody()),read))
                .subscribe(msg -> {
                }, err -> {
                }, () -> System.out.println("subscribeMsg onComplete"));

        nc.connect()
//                .doOnError(Throwable::printStackTrace)
                .retryWhen(x->x.delay(1,TimeUnit.SECONDS))
//                .doOnNext(t -> System.out.printf("Received a message: %s\n", t))
                .subscribe();

        Observable.interval(1,TimeUnit.SECONDS).subscribe(x-> System.out.printf("%d sec read: %d\n",x+1, read));

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
