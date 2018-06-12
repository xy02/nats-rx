package com.github.xy02.nats;

import io.reactivex.Observable;
import io.reactivex.Single;

import java.util.concurrent.TimeUnit;

public interface IConnection {
    //call dispose() to disconnect.
    Observable<String> connect();

    //call dispose() to unsubscribeMsg.
    Observable<MSG> subscribeMsg(String subject);

    //call dispose() to unsubscribeMsg.
    Observable<MSG> subscribeMsg(String subject, String queue);

    //publish MSG
    void publish(MSG msg);

    //return elapsed time on PONG
    Single<Long> ping(TimeUnit unit);
}
