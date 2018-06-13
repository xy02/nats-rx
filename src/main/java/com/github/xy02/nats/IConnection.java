package com.github.xy02.nats;

import io.reactivex.Observable;
import io.reactivex.Single;

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

    //emit elapsed time(ms) on PONG
    Single<Long> ping();
}
