package com.github.xy02.nats;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class Client {

    public Client(String host) throws IOException {
        this(host, new Options());
    }

    public Client(String host, Options options) throws IOException {
        BehaviorSubject<Boolean> readerLatchSubject = BehaviorSubject.createDefault(true);
        byte[] buf = new byte[1];
        Socket socket = new Socket(host, options.getPort());
        conn = Completable.create(emitter -> {
            System.out.println("create, tid" + Thread.currentThread().getId());
            isSubject.onNext(socket.getInputStream());
            osSubject.onNext(socket.getOutputStream());
            emitter.onComplete();
        }).subscribeOn(Schedulers.io())
                .andThen(readerLatchSubject)
                .flatMapSingle(x -> readLine(buf))
                .flatMapSingle(this::handleMessage)
                .doOnNext(System.out::println)
                .doOnNext(x -> readerLatchSubject.onNext(true))
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .doOnDispose(() -> {
                    System.out.println("doOnDispose, tid" + Thread.currentThread().getId());
                    osSubject.onComplete();
                    isSubject.onComplete();
                    pongSubject.onComplete();
                    msgSubject.onComplete();
                    socket.close();
                }).subscribe();
    }

    public void close() {
        conn.dispose();
    }

    public Observable<Msg> subscribeMsg(String subject) {
        return subscribeMsg(subject, "");
    }

    public synchronized Observable<Msg> subscribeMsg(String subject, String queue) {
        final int _sid = ++sid;
        byte[] subMessage = ("SUB " + subject + " " + queue + " " + _sid + "\r\n").getBytes();
        byte[] unsubMessage = ("UNSUB " + _sid + "\r\n").getBytes();
        Disposable d = osSubject.doOnNext(os -> os.write(subMessage))
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .doOnDispose(() -> {
                    System.out.println("subscribeMsg doOnDispose");
                    singleOutputStream
                            .doOnSuccess(os -> os.write(unsubMessage))
                            .subscribe(os -> {
                            }, Throwable::printStackTrace);
                })
                .subscribe();
        return msgSubject.filter(msg -> msg.getSubject().equals(subject) && msg.getSid() == _sid)
                .doOnDispose(d::dispose);
    }

    public Completable publish(Msg msg) {
        int bodyLength = msg.getBody().length;
        byte[] message = ("PUB " + msg.getSubject() + " " + msg.getReplyTo() + " " + bodyLength + "\r\n").getBytes();
        byte[] data = ByteBuffer.allocate(message.length + bodyLength + 2).put(message).put(msg.getBody()).put(BUFFER_CRLF).array();
        return singleOutputStream.doOnSuccess(os -> os.write(data))
                .toCompletable();
    }

    public Completable ping(long timeout, TimeUnit unit) {
        return singleOutputStream
                .doOnSuccess(os -> os.write(BUFFER_PING))
                .toCompletable()
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
    private Disposable conn;

    private Subject<Boolean> pongSubject = PublishSubject.create();
    private Subject<Msg> msgSubject = PublishSubject.create();
    private Subject<OutputStream> osSubject = BehaviorSubject.create();
    private Single<OutputStream> singleOutputStream = osSubject.take(1).singleOrError();
    private Subject<InputStream> isSubject = BehaviorSubject.create();
    private Single<InputStream> singleInputStream = isSubject.take(1).singleOrError();

    private Single<String> readLine(byte[] buf) {
        return singleInputStream.map(is -> {
//            System.out.println("try readLine");
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
        });
    }

    //return message type
    private Single<String> handleMessage(String message) {
        return singleOutputStream.flatMap(os -> {
            String[] arr = message.split(" ");
            Completable c;
            switch (arr[0]) {
                case TYPE_INFO:
                    c = handleInfo(arr);
                    break;
                case TYPE_MSG:
                    c = handleMsg(arr);
                    break;
                case TYPE_PING:
                    c = Completable.create(emitter -> {
                        os.write(BUFFER_PONG);
                        emitter.onComplete();
                    });
                    break;
                case TYPE_PONG:
                    c = Completable.create(emitter -> {
                        pongSubject.onNext(true);
                        emitter.onComplete();
                    });
                    break;
                case TYPE_OK:
                    c = Completable.complete();
                    break;
                case TYPE_ERR:
                    c = Completable.create(emitter -> {
                        System.out.println(message);
                        emitter.onComplete();
                    });
                    break;
                default:
                    c = Completable.error(new Exception("bad message"));
            }
            return c.andThen(Single.just(arr[0]));
        });
    }

    private Completable handleInfo(String[] data) {
        return Completable.create(emitter -> {
            System.out.println(data[1]);
            emitter.onComplete();
        });
    }

    //return message type
    private Completable handleMsg(String[] data) {
        return singleInputStream.doOnSuccess(is -> {
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
            byte[] buf = new byte[bytes];
            int index = 0;
            while (index < bytes) {
                int read = is.read(buf, index, bytes - index);
                if (read == -1)
                    throw new Exception("read -1");
                index += read;
            }
            byte[] crlf = new byte[1];
            if (is.read(crlf) != 1 || crlf[0] != 13)
                throw new Exception("bad cr");
            if (is.read(crlf) != 1 || crlf[0] != 10)
                throw new Exception("bad lf");
            msgSubject.onNext(new Msg(subject, Integer.parseInt(sid), replyTo, buf));
        }).toCompletable();
    }

}