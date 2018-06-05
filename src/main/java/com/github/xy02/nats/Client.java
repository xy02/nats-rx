package com.github.xy02.nats;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class Client {

    public Client(String host) {
        this(host, new Options());
    }

    public Client(String host, Options options) {
        this.host = host;
        this.options = options;
    }

    public Observable<String[]> connect() {
        BehaviorSubject<Boolean> readerLatchSubject = BehaviorSubject.createDefault(true);
        byte[] buf = new byte[1];
        return Observable.create(emitter -> {
            System.out.println("create, tid" + Thread.currentThread().getId());
            socket = new Socket(host, options.getPort());
            os = socket.getOutputStream();
            is = socket.getInputStream();
            emitter.onNext(socket);
        })
                .subscribeOn(Schedulers.io())
                .flatMap(x -> readerLatchSubject)
                .map(x -> readLine(buf))
                .map(str -> str.split(" "))
                .groupBy(arr -> arr[0])
                .flatMap(group -> group)
                .doOnNext(this::handleCommand)
                .doOnNext(x -> readerLatchSubject.onNext(true))
                .doOnError(err -> msgSubject.onNext(new Msg()))
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .doOnDispose(() -> {
                    System.out.println("doOnDispose, tid" + Thread.currentThread().getId());
                    pongSubject.onComplete();
                    msgSubject.onComplete();
                    socket.close();
                    os.close();
                    is.close();
                });
    }

    public Observable<Msg> subscribeMsg(String subject) {
        return subscribeMsg(subject, "");
    }

    public synchronized Observable<Msg> subscribeMsg(String subject, String queue) {
        final int _sid = ++sid;
        byte[] subMessage = ("SUB " + subject + " " + queue + " " + _sid + "\r\n").getBytes();
        byte[] unsubMessage = ("UNSUB " + _sid + "\r\n").getBytes();
        return Completable.fromObservable(Observable.just(subMessage)
                .doOnNext(x -> os.write(x)))
                .andThen(msgSubject)
                .doOnNext(msg -> {
                    if (msg.getSubject() == null)
                        throw new Exception("bad msg");
                })
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .filter(msg -> msg.getSubject().equals(subject) && msg.getSid() == _sid)
                .doOnDispose(() -> os.write(unsubMessage));
    }

    public Completable publish(Msg msg) {
        int bodyLength = msg.getBody().length;
        byte[] message = ("PUB " + msg.getSubject() + " " + msg.getReplyTo() + " " + bodyLength + "\r\n").getBytes();
        return Completable.create(e -> {
//            System.out.println("tid"+Thread.currentThread().getId());
            byte[] data = ByteBuffer.allocate(message.length + bodyLength + 2).put(message).put(msg.getBody()).put(BUFFER_CRLF).array();
            os.write(data);
            e.onComplete();
        });
    }

    public Completable ping(long timeout, TimeUnit unit) {
        return Completable.create(e -> {
//            System.out.println("tid"+Thread.currentThread().getId());
            os.write(BUFFER_PING);
            e.onComplete();
        })
                .concatWith(Completable.fromObservable(pongSubject.take(1)))
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
    private final static byte[] BUFFER_CRLF = "\r\n".getBytes();

    private int sid;
    private String host;
    private Options options;
    private Socket socket;
    private OutputStream os;
    private InputStream is;

    private Subject<Boolean> pongSubject = PublishSubject.create();
    private Subject<Msg> msgSubject = PublishSubject.create();

    private String readLine(byte[] buf) throws Exception {
        System.out.println("try readLine");
        StringBuilder sb = new StringBuilder();
        while (true) {
            int read = is.read(buf);
            if (read != 1)
                throw new Exception("bad read");
            if (buf[0] != 13) {
                sb.append((char) buf[0]);
            } else {
                read = is.read(buf);
                if (read != 1 || buf[0] != 10)
                    throw new Exception("bad lf");
                break;
            }
        }
        return sb.toString();
    }

    private void handleCommand(String[] data) throws Exception {
//        System.out.println("tid"+Thread.currentThread().getId());
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
                System.out.println(String.join(" ", data));
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
            int read = is.read(buf, index, bytes - index);
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