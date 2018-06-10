package com.github.xy02.nats;

import io.reactivex.Completable;

public class OK implements Message {
    @Override
    public Completable handle(Connection connection) {
        return Completable.complete();
    }
}
