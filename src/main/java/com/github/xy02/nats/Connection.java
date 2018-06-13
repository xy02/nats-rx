package com.github.xy02.nats;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class Connection implements IConnection {

    public Connection() throws IOException {
        this(new Options());
    }

    public Connection(Options options) throws IOException {
        init(options);
    }

    @Override
    public void close() {
        reconnectSubject.onComplete();
        outputSubject.onComplete();
        msgSubject.onComplete();
        onPongSubject.onComplete();
        onCloseSubject.onComplete();
    }

    @Override
    public Observable<Long> onReconnect() {
        return reconnectSubject;
    }

    @Override
    public Observable<MSG> subscribeMsg(String subject) {
        return subscribeMsg(subject, "");
    }

    @Override
    public Observable<MSG> subscribeMsg(String subject, String queue) {
        int _sid = plusSid();
        byte[] subMessage = ("SUB " + subject + " " + queue + " " + _sid + "\r\n").getBytes();
        byte[] unsubMessage = ("UNSUB " + _sid + "\r\n").getBytes();
        Disposable d = reconnectSubject
                .mergeWith(Observable.just(0L))
                .doOnNext(x -> System.out.printf("sub :%s\n", Thread.currentThread().getName()))
                .doOnNext(x -> outputSubject.onNext(subMessage))
                .doOnDispose(() -> outputSubject.onNext(unsubMessage))
                .subscribe();
        return msgSubject.filter(msg -> msg.getSid() == _sid && msg.getSubject().equals(subject))
                .doOnDispose(d::dispose);
    }

    @Override
    public void publish(MSG msg) {
        int bodyLength = msg.getBody().length;
        byte[] message = ("PUB " + msg.getSubject() + " " + msg.getReplyTo() + " " + bodyLength + "\r\n").getBytes();
        byte[] data = ByteBuffer.allocate(message.length + bodyLength + 2).put(message).put(msg.getBody()).put(BUFFER_CRLF).array();
        outputSubject.onNext(data);
//        System.out.printf("publish on :%s\n", Thread.currentThread().getName());
    }

    @Override
    public Single<Long> ping() {
        return Observable.interval(0, 1, TimeUnit.MILLISECONDS)
                .doOnSubscribe(d -> outputSubject.onNext(BUFFER_PING))
                .takeUntil(onPongSubject)
                .takeLast(1)
                .singleOrError()
                .timeout(5, TimeUnit.SECONDS);
    }

    private final static byte[] BUFFER_CONNECT = "CONNECT {\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"name\":\"\",\"lang\":\"java\",\"version\":\"0.2.3\",\"protocol\":0}\r\n".getBytes();
    private final static byte[] BUFFER_PONG = "PONG\r\n".getBytes();
    private final static byte[] BUFFER_PING = "PING\r\n".getBytes();
    private final static byte[] BUFFER_CRLF = "\r\n".getBytes();
    private final static String TYPE_INFO = "INFO";
    private final static String TYPE_MSG = "MSG";
    private final static String TYPE_PING = "PING";
    private final static String TYPE_PONG = "PONG";
    private final static String TYPE_OK = "+OK";
    private final static String TYPE_ERR = "-ERR";

    private byte[] buf = new byte[1024 * 64];
    private String[] fragmentArr = new String[10];
    private long reconnectTimes = 0;
    private int sid;
    private long write = 0;

    private Subject<Boolean> onPongSubject = PublishSubject.create();
    private Subject<MSG> msgSubject = PublishSubject.create();
    private Subject<byte[]> outputSubject = PublishSubject.create();
    private Subject<Long> reconnectSubject = BehaviorSubject.create();
    private Subject<Boolean> onCloseSubject = PublishSubject.create();

    private synchronized void init(Options options) throws IOException {
        Socket socket = new Socket(options.getHost(), options.getPort());
        OutputStream os = socket.getOutputStream();
        os.write(BUFFER_CONNECT);
        OutputStream outputStream = new BufferedOutputStream(os, 1024 * 64);
        InputStream inputStream = socket.getInputStream();
        System.out.printf("connect on :%s\n", Thread.currentThread().getName());
        readData(inputStream)
                .subscribeOn(Schedulers.newThread())
                .mergeWith(writeData(outputStream))
                .mergeWith(flushData(outputStream))
                .takeUntil(onCloseSubject)
                .doOnTerminate(socket::close)
                .doOnDispose(socket::close)
                .doOnError(x -> reconnect(options))
                .subscribe(x -> {
                }, err -> {
                });
    }

    private void reconnect(Options options) {
        int interval = options.getReconnectInterval();
        if (interval == 0) {
            reconnectSubject.onComplete();
            return;
        }
        Observable.timer(interval, TimeUnit.SECONDS)
                .doOnComplete(() -> init(options))
                .doOnComplete(() -> reconnectSubject.onNext(++reconnectTimes))
                .retry()
                .subscribe();
    }

    private synchronized int plusSid() {
        return ++sid;
    }

    private Observable<Long> flushData(OutputStream outputStream) {
        return Observable.interval(10000, 100, TimeUnit.MICROSECONDS)
                .doOnNext(x -> outputStream.flush())
                ;
    }

    private Observable<Long> writeData(OutputStream outputStream) {
        return outputSubject
                .doOnNext(data -> outputStream.write(data))
                .map(x -> ++write);
    }

    private Observable<Long> readData(InputStream inputStream) {
        return Observable.create(emitter -> {
            System.out.printf("read on :%s\n", Thread.currentThread().getName());
            int fragment = 0;
            int temp = 0;
            int read;
            while ((read = inputStream.read(buf, temp, buf.length - temp)) != -1) {
                read += temp;
                int offset = 0;
                for (int i = 0; i < read; i++) {
                    byte b = buf[i];
                    if (b == 32 || b == 13) {
                        if (i != offset) {
                            fragmentArr[fragment] = new String(buf, offset, i - offset);
                            fragment++;
                        }
                        offset = i + 1;
                        continue;
                    }
                    if (b == 10) {
                        if (fragment != 0) {
                            String messageType = fragmentArr[0];
//                            emitter.onNext(messageType);
                            switch (messageType) {
                                case TYPE_INFO:
                                    System.out.println(fragmentArr[1]);
                                    break;
                                case TYPE_MSG:
                                    offset = parseMSG(fragmentArr, fragment, inputStream, i + 1, read) - 1;
//                                    System.out.printf("offset:%d\n", offset);
                                    i = offset;
                                    break;
                                case TYPE_PING:
                                    outputSubject.onNext(BUFFER_PONG);
                                    break;
                                case TYPE_PONG:
                                    onPongSubject.onNext(true);
                                    break;
                                case TYPE_OK:
                                    System.out.println(TYPE_OK);
                                    break;
                                case TYPE_ERR:
                                    for (int x = 0; x < fragment; x++)
                                        System.out.printf("%s ", fragmentArr[x]);
                                    System.out.println();
                                    break;
                                default:
                                    throw new Exception("bad message type");
                            }
                            fragment = 0;
                        }
                        offset = i + 1;
//                    continue;
                    }
//                    if (b < 0)
//                        throw new Exception("bad message");
                }
                if (offset == read) {
                    temp = 0;
                    continue;
                }
                //move rest of buf to the start
                temp = read - offset;
//                System.out.printf("read:%d, offset:%d, temp:%d\n", read, offset, temp);
                System.arraycopy(buf, offset, buf, 0, temp);
            }
            throw new Exception("read -1");
        });
    }

    private int parseMSG(String[] arr, int fragment, InputStream inputStream, int offset, int max) throws Exception {
        String subject = arr[1];
        String sid = arr[2];
        String replyTo = "";
        int length;
        if (fragment == 4)
            length = Integer.parseInt(arr[3]);
        else {
            replyTo = arr[3];
            length = Integer.parseInt(arr[4]);
        }
        byte[] data = new byte[length];
        int lengthWithCRLF = length + 2;
        if (offset + lengthWithCRLF > max) {
//            System.out.printf("offset:%d, length:%d-------------------", offset, length);
            int index = max - offset;
            if (index > length) {
                //superfluous CR
                System.arraycopy(buf, offset, data, 0, length);
                offset += length;
            } else {
                //first,copy from buf to data
                System.arraycopy(buf, offset, data, 0, index);
                //read rest data
                while (index < length) {
                    int read = inputStream.read(data, index, length - index);
                    if (read == -1)
                        throw new Exception("read -1");
                    index += read;
                }
                offset = max;
            }

        } else {
            System.arraycopy(buf, offset, data, 0, length);
            offset += lengthWithCRLF;
        }
        msgSubject.onNext(new MSG(subject, Integer.parseInt(sid), replyTo, data));
        return offset;
    }

}
