package com.github.xy02.nats;

import io.reactivex.Completable;

interface Message {
    Completable handle(Connection connection);
}
