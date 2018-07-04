import com.github.xy02.nats.Connection;
import com.github.xy02.nats.IConnection;
import io.reactivex.Observable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Request {
    public static void main(String[] args) {
        try {
            IConnection nc = new Connection();
            byte[] buf = "hello".getBytes();
            Observable
                    .interval(2 * 1000000, 30, TimeUnit.MICROSECONDS)
                    .flatMapSingle(x ->
                            nc.request("req.res", buf, 1, TimeUnit.SECONDS)
                    )
                    .subscribe(x -> {
                    }, err -> {
                    });

            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
