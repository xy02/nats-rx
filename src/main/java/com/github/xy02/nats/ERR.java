package com.github.xy02.nats;

import io.reactivex.Completable;

import java.util.ArrayList;

public class ERR implements Message {
    private String message;

    public ERR(ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String str : list) {
            sb.append(str);
        }
        message = sb.toString();
    }
    @Override
    public Completable handle(Connection connection) {
        return Completable.create(emitter -> {
            System.out.println(message);
            emitter.onComplete();
        });
    }
}
