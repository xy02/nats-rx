package com.github.xy02.nats;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;

public class PING implements Message {
    private final static byte[] BUFFER_PONG = "PONG\r\n".getBytes();

    @Override
    public Completable handle(Connection connection) {
        return connection.singleOutputStream
                .doOnSuccess(outputStream -> outputStream.write(BUFFER_PONG))
                .subscribeOn(Schedulers.io())
                .toCompletable();
    }
}
