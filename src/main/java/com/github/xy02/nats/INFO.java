package com.github.xy02.nats;

import io.reactivex.Completable;

public class INFO implements Message {
    private String json;

    public INFO(String json) {
        this.json = json;
    }

    @Override
    public Completable handle(Connection connection) {
        return Completable.create(emitter -> {
            System.out.println(json);
            emitter.onComplete();
        });
    }
}
