import com.github.xy02.nats.Connection;
import com.github.xy02.nats.IConnection;
import com.github.xy02.nats.MSG;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        try {
//            new Main().test("sub1", 1);
//            new Main().test("sub1", -1);
            new Main().test("sub1", 0);

            Thread.sleep(Long.MAX_VALUE);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public long read = 0;

    public long length = 0;

    public void test(String subject, int type) {
        Single.create(emitter -> {
            //create connection
            IConnection nc = new Connection();
            //request
//            nc.request("aips.test", "cPort".getBytes(), 1, TimeUnit.SECONDS)
//                    .doOnSuccess(msg -> System.out.printf("msg length: %d", msg.getBody().length))
//                    .doOnSuccess(msg -> System.out.printf("Received a msg: %s, thread:%s\n", new String(msg.getBody()), Thread.currentThread().getName()))
//                    .subscribe();
            //sub
            if (type >= 0) {
                Disposable sub = nc.subscribeMsg(subject)
//                .takeUntil(Observable.timer(10, TimeUnit.SECONDS))
                        .doOnComplete(() -> System.out.printf("read: %d\n", read))
//                        .observeOn(Schedulers.computation())
                        .doOnNext(msg -> read++)
//                .doOnNext(msg -> length += msg.getBody().length)
//                .doOnNext(msg -> System.out.printf("Received a msg: %s, thread:%s\n", new String(msg.getBody()),Thread.currentThread().getName()))
                        .subscribe(msg -> {
                        }, err -> {
                        }, () -> System.out.println("subscribeMsg onComplete"));
            }
            //close
//            Observable.timer(6, TimeUnit.SECONDS)
//                    .doOnComplete(() -> sub.dispose())
//                    .subscribe();
//            Observable.timer(3, TimeUnit.SECONDS)
//                    .doOnComplete(() -> nc.close())
//                    .subscribe();

            //ping
//            Observable
//                    .interval(3, TimeUnit.SECONDS)
//                    .flatMapSingle(l -> nc.ping().map(t -> "ping ms:" + t)
//                            .doOnSuccess(System.out::println))
//                    .doOnError(Throwable::printStackTrace)
//                    .retry()
//                    .subscribe()
//            ;

            //log
            Observable.interval(1, TimeUnit.SECONDS)
                    .subscribe(x -> System.out.printf("%d sec read: %d, ops: %d/s\n", x + 1, read, read / (x + 1)))
            ;

            //pub
            if (type <= 0) {
                MSG testMsg = new MSG(subject, "hello".getBytes());
                Observable.create(emitter1 -> {
                    System.out.printf("publish on 1 :%s\n", Thread.currentThread().getName());
                    while (true) {
                        nc.publish(testMsg);
//                        Thread.yield();
//                        Thread.sleep(1000);
                    }
                }).subscribeOn(Schedulers.io()).subscribe();
            }
//            Observable.create(emitter1 -> {
//                System.out.printf("publish on 2 :%s\n", Thread.currentThread().getName());
//                while (true) {
//                    nc.publish(testMsg);
//                    //Thread.sleep(1000);
//                }
//            }).subscribeOn(Schedulers.newThread()).subscribe();
            System.out.printf("11111\n");

        })
//                .subscribeOn(Schedulers.io())
                .doOnError(Throwable::printStackTrace)
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .subscribe()
        ;
    }

}
