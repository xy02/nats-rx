package com.github.xy02.nats;

import io.reactivex.Completable;

public class Pong implements Command {
    @Override
    public Completable execute(Client client) {
        return Completable.create(e->{
            client.onPong();
            e.onComplete();
        });
    }
}
