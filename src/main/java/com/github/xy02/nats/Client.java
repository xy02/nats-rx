package com.github.xy02.nats;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class Client {

    private final static String TYPE_INFO = "INFO";
    private final static String TYPE_MSG = "MSG";
    private final static String TYPE_PING = "PING";
    private final static String TYPE_PONG = "PONG";
    private final static String TYPE_OK = "+OK";
    private final static String TYPE_ERR = "-ERR";
    private final static byte[] BUFFER_PONG = "PONG\r\n".getBytes();
    private final static byte[] BUFFER_PING = "PING\r\n".getBytes();

    private OutputStream os;

    private PublishSubject<Boolean> pongSubject = PublishSubject.create();

    protected void onPong(){
        pongSubject.onNext(true);
    }

    private Client(String host, Options options) throws IOException {
        Socket socket = new Socket(host, options.getPort());
        InputStream is = socket.getInputStream();
        os = socket.getOutputStream();

        BehaviorSubject<Boolean> readerLatchSubject = BehaviorSubject.createDefault(true);
        Observable<String> dataObs = Observable.create(e -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            readerLatchSubject.subscribe(x -> {
                System.out.println("try readLine");
                String str = reader.readLine();
                e.onNext(str);
            });
        });

        dataObs.map(str -> str.split(" "))
                .groupBy(arr -> arr[0])
                .flatMap(group -> group)
                .map(this::parseCommand)
                .doOnNext(x -> readerLatchSubject.onNext(true))
                .flatMapCompletable(command -> command.execute(this))
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    private Command parseCommand(String[] data) throws Exception {
        switch (data[0]) {
            case TYPE_INFO:
                return parseInfo(data);
            case TYPE_MSG:
                return parseMsg(data);
            case TYPE_PING:
                return parsePing(data);
            case TYPE_PONG:
                return parsePong(data);
            case TYPE_OK:
                return parseOK(data);
            case TYPE_ERR:
                return parseErr(data);
        }
        throw new Exception("bad data");
    }

    private Command parseInfo(String[] data) {
        System.out.println(data[1]);
        return new Info();
    }

    private Command parseMsg(String[] data) {
        System.out.println(data);
        return null;
    }

    private Command parsePing(String[] data) {
        System.out.println(data[0]);
        return new Ping();
    }

    private Command parsePong(String[] data) {
        System.out.println(data[0]);
        return new Pong();
    }

    private Command parseOK(String[] data) {
        System.out.println(data);
        return null;
    }

    private Command parseErr(String[] data) {
        System.out.println(data);
        return null;
    }

    public static Single<Client> connect(String host) {
        return connect(host, new Options());
    }

    public static Single<Client> connect(String host, Options options) {
        return Single.create(emitter -> {
            Client c = new Client(host, options);
            emitter.onSuccess(c);
        });
    }

    public Observable<Message> subscribe(String subject, String queue) {
        return null;
    }

    public Completable publish(Message message) {
        return null;
    }

    public Completable ping(long timeout, TimeUnit unit) {
        return Completable.create(e->{
            os.write(BUFFER_PING);
            e.onComplete();
        }).concatWith(Completable.fromObservable(pongSubject.take(1)))
                .timeout(timeout, unit);
    }

    protected Completable pong() {
        return Completable.create(e->{
            os.write(BUFFER_PONG);
            e.onComplete();
        });
    }
}
