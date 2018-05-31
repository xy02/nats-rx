package com.github.xy02.nats;

import io.reactivex.Completable;

public class Ping implements Command {
    @Override
    public Completable execute(Client client) {
        return client.pong();
    }
}
