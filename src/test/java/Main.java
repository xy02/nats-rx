import com.github.xy02.nats.Connection;
import com.github.xy02.nats.MSG;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.concurrent.TimeUnit;

public class Main {

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

    public long read = 0;

    public long length = 0;

    public void test(String subject) {
        Single.create(emitter -> {
            //create connection
            Connection nc = new Connection();
            //sub
            Disposable sub = nc.subscribeMsg(subject)
//                .takeUntil(Observable.timer(10, TimeUnit.SECONDS))
                    .doOnComplete(() -> System.out.printf("read: %d\n", read))
                    .doOnNext(msg -> read++)
//                .doOnNext(msg -> length += msg.getBody().length)
//                .doOnNext(msg -> System.out.printf("Received a msg: %s, i:%d\n", new String(msg.getBody()),read))
                    .subscribe(msg -> {
                    }, err -> {
                    }, () -> System.out.println("subscribeMsg onComplete"));
            //close
//            Observable.timer(6, TimeUnit.SECONDS)
//                    .doOnComplete(() -> sub.dispose())
//                    .subscribe();
//            Observable.timer(3, TimeUnit.SECONDS)
//                    .doOnComplete(() -> nc.close())
//                    .subscribe();

            //ping
            Observable
                    .interval(3, TimeUnit.SECONDS)
                    .flatMapSingle(l -> nc.ping().map(t -> "ping ms:" + t)
                            .doOnSuccess(System.out::println))
                    .retry()
                    .subscribe()
            ;

            //log
            Observable.interval(1, TimeUnit.SECONDS)
                    .subscribe(x -> System.out.printf("%d sec read: %d, ops: %d/s\n", x + 1, read, read / (x + 1)))
            ;

            //pub
            MSG testMsg = new MSG(subject, "hello".getBytes());
            Observable.create(emitter1 -> {
                System.out.printf("publish on 1 :%s\n", Thread.currentThread().getName());
                while (true) {
                    nc.publish(testMsg);
                    //Thread.sleep(1000);
                }
            }).subscribeOn(Schedulers.newThread()).subscribe();
            Observable.create(emitter1 -> {
                System.out.printf("publish on 2 :%s\n", Thread.currentThread().getName());
                while (true) {
                    nc.publish(testMsg);
                    //Thread.sleep(1000);
                }
            }).subscribeOn(Schedulers.newThread()).subscribe();
            System.out.printf("11111\n");

        })
//                .subscribeOn(Schedulers.io())
                .doOnError(Throwable::printStackTrace)
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .subscribe()
        ;
    }

}
