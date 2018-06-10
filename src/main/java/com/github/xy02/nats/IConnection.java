package com.github.xy02.nats;

import io.reactivex.Completable;
import io.reactivex.Observable;

public interface IConnection {
    //call dispose() to disconnect.
    Completable connect();
    //call dispose() to unsubscribeMsg.
    Observable<MSG> subscribeMsg();
    //publish MSG
    Completable publish(MSG msg);
    //Received PONG, it completes.
    Completable ping();
}
