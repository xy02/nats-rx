package com.github.xy02.nats;

import io.reactivex.Completable;

public class PING implements Message {
    private final static byte[] BUFFER_PONG = "PONG\r\n".getBytes();
    @Override
    public Completable handle(Connection connection) {
        return Completable.create(emitter -> {
            connection.os.write(BUFFER_PONG);
            emitter.onComplete();
        });
    }
}
