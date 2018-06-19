package com.github.xy02.nats;

import io.reactivex.Observable;
import io.reactivex.Single;

import java.util.concurrent.TimeUnit;

public interface IConnection {
    //close connection
    void close();

    //Emit reconnect times.
    Observable<Long> onReconnect();

    //call dispose() to unsubscribeMsg.
    Observable<MSG> subscribeMsg(String subject);

    //call dispose() to unsubscribeMsg.
    Observable<MSG> subscribeMsg(String subject, String queue);

    //publish MSG
    void publish(MSG msg);

    //request MSG
    Single<MSG>request(String subject, byte[] body, long timeout, TimeUnit timeUnit);

    //emit elapsed time(ms) on PONG
    Single<Long> ping();
}
