package com.github.xy02.nats;

import io.reactivex.Completable;

public class Info implements Command {
    @Override
    public Completable execute(Client client) {
        return Completable.complete();
    }
}
