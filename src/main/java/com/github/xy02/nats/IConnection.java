package com.github.xy02.nats;

import io.reactivex.Completable;
import io.reactivex.Observable;

public interface IConnection {
    //call dispose() to disconnect.
    Observable<String> connect();
    //call dispose() to unsubscribeMsg.
    Observable<MSG> subscribeMsg(String subject);
    //call dispose() to unsubscribeMsg.
    Observable<MSG> subscribeMsg(String subject, String queue);
    //publish MSG
    void publish(MSG msg);
    //Received PONG, it completes.
    Completable ping();
}
