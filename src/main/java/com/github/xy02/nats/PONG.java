package com.github.xy02.nats;

import io.reactivex.Completable;

public class PONG implements Message {
    @Override
    public Completable handle(Connection connection) {
        return Completable.create(emitter -> {
            connection.pongSubject.onNext(true);
            emitter.onComplete();
        });
    }
}
