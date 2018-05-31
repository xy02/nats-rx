package com.github.xy02.nats;

import io.reactivex.Completable;

public interface Command {
    Completable execute(Client client);
}
