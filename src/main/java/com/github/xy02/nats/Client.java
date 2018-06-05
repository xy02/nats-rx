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

    public static Single<Client> connect(String host) {
        return connect(host, new Options());
    }

    public static Single<Client> connect(String host, Options options) {
        return Single.create(emitter -> {
            Client c = new Client(host, options);
            emitter.onSuccess(c);
        });
    }

    public  Observable<Msg> subscribe(String subject) {
        return subscribe(subject,"");
    }

    public synchronized Observable<Msg> subscribe(String subject, String queue) {
        final int _sid = ++sid;
        return Completable.fromObservable(Observable.just(_sid)
                .map(sid -> new StringBuilder("SUB ").append(subject).append(" ").append(queue).append(" ").append(sid).append("\r\n").toString().getBytes())
                .doOnNext(x -> os.write(x)))
                .andThen(msgSubject)
                .filter(msg -> msg.getSubject().equals(subject) && msg.getSid() == _sid)
                .doOnDispose(()->os.write(new StringBuilder("UNSUB ").append(_sid).append("\r\n").toString().getBytes()));
    }

    public Completable publish(Msg msg) {
        return null;
    }

    public Completable ping(long timeout, TimeUnit unit) {
        return Completable.create(e -> {
            os.write(BUFFER_PING);
            e.onComplete();
        }).concatWith(Completable.fromObservable(pongSubject.take(1)))
                .timeout(timeout, unit);
    }

    private final static String TYPE_INFO = "INFO";
    private final static String TYPE_MSG = "MSG";
    private final static String TYPE_PING = "PING";
    private final static String TYPE_PONG = "PONG";
    private final static String TYPE_OK = "+OK";
    private final static String TYPE_ERR = "-ERR";
    private final static byte[] BUFFER_PONG = "PONG\r\n".getBytes();
    private final static byte[] BUFFER_PING = "PING\r\n".getBytes();
    private int sid;

    private OutputStream os;
    private InputStream is;

    private Subject<Boolean> pongSubject = PublishSubject.create();
    private Subject<Msg> msgSubject = PublishSubject.create();


    private Client(String host, Options options) throws IOException {
        Socket socket = new Socket(host, options.getPort());
        os = socket.getOutputStream();
        is = socket.getInputStream();

        BehaviorSubject<Boolean> readerLatchSubject = BehaviorSubject.createDefault(true);
        observeMessage(is, readerLatchSubject)
                .map(str -> str.split(" "))
                .groupBy(arr -> arr[0])
                .flatMap(group -> group)
                .doOnNext(this::handleCommand)
                .doOnNext(x -> readerLatchSubject.onNext(true))
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    private Observable<String> observeMessage(InputStream ins,BehaviorSubject<Boolean> readerLatchSubject){
        return Observable.<String>create(emitter -> {
            byte[] buf = new byte[1];
            readerLatchSubject.subscribe(x -> {
                System.out.println("try readLine");
                StringBuilder sb = new StringBuilder();
                while (true) {
                    int read = ins.read(buf);
                    if (read != 1)
                        throw new Exception("bad read");
                    if(buf[0] != 13){
                        sb.append((char)buf[0]);
                    }else{
                        read = ins.read(buf);
                        if (read != 1 || buf[0] != 10)
                            throw new Exception("bad lf");
                        break;
                    }
                }
                emitter.onNext(sb.toString());
            });
        }).subscribeOn(Schedulers.io());
    }

    private void handleCommand(String[] data) throws Exception {
        switch (data[0]) {
            case TYPE_INFO:
                handleInfo(data);
                return;
            case TYPE_MSG:
                handleMsg(data);
                return;
            case TYPE_PING:
                os.write(BUFFER_PONG);
                return;
            case TYPE_PONG:
//                System.out.println(data[0]);
                pongSubject.onNext(true);
                return;
            case TYPE_OK:
                System.out.println(data[0]);
                return;
            case TYPE_ERR:
                System.out.println(data[0]+":"+data[1]);
                return;
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
        int bytes;
        if (data.length == 4)
            bytes = Integer.parseInt(data[3]);
        else {
            replyTo = data[3];
            bytes = Integer.parseInt(data[4]);
        }
//        System.out.println( new StringBuilder("onMessage ").append(subject).append(" ").append(sid).append(" ").append(replyTo).append(" ").append(bytes).toString());
        byte[] buf = new byte[bytes];
        int index = 0;
        while (index < bytes) {
            int read = is.read(buf, index, bytes-index);
            if (read == -1)
                throw new Exception("read -1");
            index += read;
        }
//        System.out.println("index"+index+":"+new String(buf));
        byte[] crlf = new byte[1];
        if (is.read(crlf) != 1 || crlf[0] != 13)
            throw new Exception("bad cr");
        if (is.read(crlf) != 1 || crlf[0] != 10)
            throw new Exception("bad lf");
        msgSubject.onNext(new Msg(subject, Integer.parseInt(sid), replyTo, buf));
    }


}
