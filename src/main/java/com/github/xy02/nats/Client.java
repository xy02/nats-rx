package com.github.xy02.nats;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

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
    private int sid;
    private byte[] crlf = new byte[2];
    private Subject<Message> msgSubject = PublishSubject.create();

    private OutputStream os;
    private InputStream is;

    private Subject<Boolean> pongSubject = PublishSubject.create();

    private void onPong() {
        pongSubject.onNext(true);
    }

    private Client(String host, Options options) throws IOException {
        Socket socket = new Socket(host, options.getPort());
        is = socket.getInputStream();
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
                .doOnNext(this::handleCommand)
                .doOnNext(x -> readerLatchSubject.onNext(true))
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    private void handleCommand(String[] data) throws Exception {
        switch (data[0]) {
            case TYPE_INFO:
                handleInfo(data);
            case TYPE_MSG:
                handleMsg(data);
            case TYPE_PING:
                pong();
            case TYPE_PONG:
                onPong();
            case TYPE_OK:
                System.out.println(data[0]);
            case TYPE_ERR:
                System.out.println(data[0]);
        }
        throw new Exception("bad data");
    }

    private void handleInfo(String[] data) {
        System.out.println(data[1]);
    }

    private void handleMsg(String[] data) throws Exception {
        if (data.length < 4)
            throw new Exception("wrong msg");
        String subject = data[1];
        String sid = data[2];
        String replyTo = "";
        int bytes = 0;
        if (data.length == 4)
            bytes = Integer.parseInt(data[3]);
        else {
            replyTo = data[3];
            bytes = Integer.parseInt(data[4]);
        }
        byte[] buf = new byte[bytes];
        int index = is.read(buf);
        if (index != bytes)
            throw new Exception("bad length of msg body");
        index = is.read(crlf);
        if (index != 2 || !new String(crlf).equals("\r\n"))
            throw new Exception("bad crlf");
        msgSubject.onNext(new Message(subject, Integer.parseInt(sid), replyTo, buf));
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

    public synchronized Observable<Message> subscribe(String subject, String queue) {
        final int _sid = ++sid;
        return Completable.fromObservable(Observable.just(_sid)
                .map(sid -> new StringBuilder("SUB ").append(subject).append(" ").append(queue).append(" ").append(sid).append("\r\n").toString().getBytes())
                .doOnNext(x -> os.write(x)))
                .andThen(msgSubject)
                .filter(message -> message.getSubject().equals(subject) && message.getSid() == _sid);
    }

    public Completable publish(Message message) {
        return null;
    }

    public Completable ping(long timeout, TimeUnit unit) {
        return Completable.create(e -> {
            os.write(BUFFER_PING);
            e.onComplete();
        }).concatWith(Completable.fromObservable(pongSubject.take(1)))
                .timeout(timeout, unit);
    }

    private Completable pong() {
        return Completable.create(e -> {
            os.write(BUFFER_PONG);
            e.onComplete();
        });
    }
}
