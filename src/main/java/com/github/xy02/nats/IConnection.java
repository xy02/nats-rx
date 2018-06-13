package com.github.xy02.nats;

import io.reactivex.Observable;
import io.reactivex.Single;

import java.io.IOException;

public interface IConnection {
    //close connection
    void close() throws IOException;

    //Emit reconnect times.
    Observable<Long> onReconnect();

    //call dispose() to unsubscribeMsg.
    Observable<MSG> subscribeMsg(String subject);

    //call dispose() to unsubscribeMsg.
    Observable<MSG> subscribeMsg(String subject, String queue);

    //publish MSG
    void publish(MSG msg) throws IOException;

    //emit elapsed time(ms) on PONG
    Single<Long> ping();
}
