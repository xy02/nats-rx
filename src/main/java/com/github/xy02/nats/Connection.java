package com.github.xy02.nats;

import de.huxhorn.sulky.ulid.ULID;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.github.xy02.nats.Options.REQUEST_PREFIX;

public class Connection implements IConnection {


    public Connection() throws IOException {
        this(new Options());
    }

    public Connection(Options options) throws IOException {
        init(options);

        //new request style
        subscribeMsg(myRequestPrefix + "*")
                .doOnNext(msg -> {
                    String subject = msg.getSubject();
                    ObservableEmitter<MSG> emitter = requestEmitters.get(subject);
                    if (emitter != null) {
                        emitter.onNext(msg);
                        emitter.onComplete();
                    }
                })
//                .doOnNext(onResponseSubject::onNext)
                .subscribe();
    }

    @Override
    public void close() {
        reconnectSubject.onComplete();
//        outputSubject.onComplete();
//        msgSubject.onComplete();
        onPongSubject.onComplete();
        onCloseSubject.onComplete();
        writeFlushSubject.onComplete();
//        onResponseSubject.onComplete();
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
        long _sid = plusSid();
        byte[] subMessage = ("SUB " + subject + " " + queue + " " + _sid + "\r\n").getBytes();
        byte[] unsubMessage = ("UNSUB " + _sid + "\r\n").getBytes();
        Observable<MSG> subMsg = Observable.create(emitter -> {
            msgEmitters.put(_sid, emitter);
        });
//        Observable<MSG> subMsg = msgSubject
//                .filter(msg -> msg.getSid() == _sid);
        if (options.getSubScheduler() != null)
            subMsg = subMsg.observeOn(options.getSubScheduler());
        return subMsg
                .mergeWith(reconnectSubject
                                .mergeWith(Observable.just(0L))
                                .doOnNext(x -> writeFlushSubject.onNext(subMessage))
//                        .doOnNext(x -> System.out.printf("sub:%s(queue:'%s') on %s\n", subject, queue, Thread.currentThread().getName()))
                                .doOnDispose(() -> writeFlushSubject.onNext(unsubMessage))
//                        .doOnDispose(() -> System.out.printf("unsub:%s(queue:'%s') on %s\n", subject, queue, Thread.currentThread().getName()))
                                .ofType(MSG.class)
                )
                .doFinally(() -> msgEmitters.remove(_sid))
                ;
    }

//    @Override
//    public void publish(MSG msg) {
//        int bodyLength = msg.getBody().length;
//        byte[] message = ("PUB " + msg.getSubject() + " " + msg.getReplyTo() + " " + bodyLength + "\r\n").getBytes();
//        byte[] data = ByteBuffer.allocate(message.length + bodyLength + 2).put(message).put(msg.getBody()).put(BUFFER_CRLF).array();
//        outputSubject.onNext(data);
////        System.out.printf("publish on :%s\n", Thread.currentThread().getName());
//    }

    @Override
    public void publish(MSG msg) throws IOException {
        int bodyLength = msg.getBody().length;
        byte[] message = ("PUB " + msg.getSubject() + " " + msg.getReplyTo() + " " + bodyLength + "\r\n").getBytes();
        byte[] data = ByteBuffer.allocate(message.length + bodyLength + 2).put(message).put(msg.getBody()).put(BUFFER_CRLF).array();
        os.write(data);
//        System.out.printf("publish on :%s\n", Thread.currentThread().getName());
    }

//    @Override
//    public Single<MSG> request(String subject, byte[] body, long timeout, TimeUnit timeUnit) {
//        if (options.isUseOldRequestStyle()) {
//            String reply = REQUEST_PREFIX + ulid.nextULID();
//            return subscribeMsg(reply)
//                    .mergeWith(Observable.create(emitter -> {
//                                this.publish(new MSG(subject, reply, body));
//                                emitter.onComplete();
//                            })
//                    )
//                    .take(1)
//                    .singleOrError()
//                    .timeout(timeout, timeUnit)
//                    ;
//        }
//        return newRequest(subject,body,timeout,timeUnit);
//    }

    @Override
    public Single<MSG> request(String subject, byte[] body, long timeout, TimeUnit timeUnit) {
        long id = plusRequestID();
        String reply = myRequestPrefix + id;
        return Observable.<MSG>create(emitter -> {
            requestEmitters.put(reply, emitter);
        })
                .take(1)
                .mergeWith(Observable.create(emitter -> {
                            this.publish(new MSG(subject, reply, body));
                            emitter.onComplete();
                        })
                )
                .singleOrError()
                .timeout(timeout, timeUnit)
                .doFinally(() -> requestEmitters.remove(reply))
                ;
//        return onResponseSubject
//                .filter(msg -> msg.getSubject().equals(reply))
//                .take(1)
//                .mergeWith(Observable.create(emitter -> {
//                            this.publish(new MSG(subject, reply, body));
//                            emitter.onComplete();
//                        })
//                )
//                .singleOrError()
//                .timeout(timeout, timeUnit)
//                ;
    }

    private synchronized long plusRequestID() {
        return ++requestID;
    }

    @Override
    public Single<Long> ping() {
        return Observable.interval(0, 1, TimeUnit.MILLISECONDS)
                .takeUntil(onPongSubject)
                .mergeWith(Observable.timer(0, TimeUnit.MILLISECONDS)
                        .doOnNext(x -> writeFlushSubject.onNext(BUFFER_PING))
                )
                .takeLast(1)
                .singleOrError()
                .timeout(5, TimeUnit.SECONDS);
    }

    private ULID ulid = new ULID();
    private String myRequestPrefix = REQUEST_PREFIX + ulid.nextULID() + ".";

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
    private final static byte CR = (byte) '\r';
    private final static byte LF = (byte) '\n';
    private final static byte SPACE = (byte) ' ';

    private byte[] buf = new byte[1024 * 64];
    //min bound of buf
    private int min = 0;
    //max bound of buf
    private int max = 0;

    private long reconnectTimes = 0;
    private long sid;
    private long requestID;
    private long write = 0;
    private Options options;
    private volatile OutputStream os;

    private Subject<Boolean> onPongSubject = PublishSubject.create();
    //    private Subject<MSG> msgSubject = PublishSubject.create();
    //    private Subject<byte[]> outputSubject = PublishSubject.create();
    private Subject<byte[]> writeFlushSubject = PublishSubject.create();
    private Subject<Long> reconnectSubject = BehaviorSubject.create();
    private Subject<Boolean> onCloseSubject = PublishSubject.create();

    private Map<String, ObservableEmitter<MSG>> requestEmitters = new ConcurrentHashMap<>();
    private Map<Long, ObservableEmitter<MSG>> msgEmitters = new ConcurrentHashMap<>();

    private synchronized void init(Options options) throws IOException {
        this.options = options;
        Socket socket;
        if (options.isTls()) {
            socket = SSLSocketFactory.getDefault().createSocket(options.getHost(), options.getPort());
        } else {
            socket = new Socket(options.getHost(), options.getPort());
        }
        OutputStream os = socket.getOutputStream();
        os.write(BUFFER_CONNECT);
        OutputStream outputStream = new BufferedOutputStream(os, 1024 * 64);
        this.os = outputStream;
        InputStream inputStream = socket.getInputStream();
        System.out.printf("connect on :%s\n", Thread.currentThread().getName());
        readData(inputStream, outputStream)
//                .mergeWith(writeData(outputStream))
                .mergeWith(flushData(outputStream))
                .mergeWith(writeFlushData(outputStream))
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

    private synchronized long plusSid() {
        return ++sid;
    }

    private Observable<Long> flushData(OutputStream outputStream) {
        return Observable.interval(options.getFlushInterval(), TimeUnit.MICROSECONDS, Schedulers.io())
                .doOnNext(x -> outputStream.flush())
                ;
    }

    //    private Observable<Long> writeData(OutputStream outputStream) {
//        return outputSubject
//                .doOnNext(outputStream::write)
//                .map(x -> ++write);
//    }
//
    private Observable<Long> writeFlushData(OutputStream outputStream) {
        return writeFlushSubject
                .doOnNext(data -> {
                    outputStream.write(data);
                    outputStream.flush();
                })
                .map(x -> ++write);
    }

    private Observable<Long> readData(InputStream inputStream, OutputStream outputStream) {
        return Observable.<Long>create(emitter -> {
            System.out.printf("read on: %s\n", Thread.currentThread().getName());
            min = 0;
            max = 0;
            while (!Thread.interrupted()) {
                String messageType = readString(inputStream);
//                System.out.printf("messageType:%s.\n",messageType);
                switch (messageType) {
                    case TYPE_MSG:
                        readMSG(inputStream);
                        break;
                    case TYPE_INFO:
                        String json = readLine(inputStream);
                        System.out.println(json);
                        break;
                    case TYPE_PING:
//                        outputSubject.onNext(BUFFER_PONG);
                        outputStream.write(BUFFER_PONG);
                        break;
                    case TYPE_PONG:
                        onPongSubject.onNext(true);
                        break;
                    case TYPE_OK:
                        System.out.println(TYPE_OK);
                        break;
                    case TYPE_ERR:
                        String err = readLine(inputStream);
                        System.out.println(err);
                        break;
                    default:
                        throw new Exception("bad message type");
                }
                readLF(inputStream);
            }
        }).doOnError(Throwable::printStackTrace).subscribeOn(options.getReadScheduler());
    }

    private void readLF(InputStream inputStream) throws Exception {
        while (true) {
            while (min < max) {
                if (buf[min] == LF) {
                    min++;
                    return;
                }
                min++;
            }
            moveRemain(inputStream, max);
        }
    }

    private String readString(InputStream inputStream) throws Exception {
        int offset = min;
        while (true) {
            while (min < max) {
                byte b = buf[min];
                if (b == SPACE || b == CR) {
                    String str = new String(buf, offset, min - offset);
                    min++;
                    return str;
                }
                min++;
            }
            moveRemain(inputStream, offset);
            offset = 0;
        }
    }

    private String readLine(InputStream inputStream) throws Exception {
        int offset = min;
        while (true) {
            while (min < max) {
                if (buf[min] == CR) {
                    String str = new String(buf, offset, min - offset);
                    min++;
                    return str;
                }
                min++;
            }
            moveRemain(inputStream, offset);
            offset = 0;
        }
    }

    //move rest of buf to the start
    private void moveRemain(InputStream inputStream, int offset) throws Exception {
        //move rest of buf to the start
        min = max - offset;
        if (min > 0)
            System.arraycopy(buf, offset, buf, 0, min);
        int read = inputStream.read(buf, min, buf.length - min);
        if (read == -1)
            throw new Exception("read -1");
        max = min + read;
    }

    private void readMSG(InputStream inputStream) throws Exception {
        String subject = readString(inputStream);
        String sid = readString(inputStream);
        String replyTo = readString(inputStream);
        String length;
        if (buf[min - 1] == CR) {
            length = replyTo;
            replyTo = "";
        } else {
            length = readString(inputStream);
        }
        readLF(inputStream);
        byte[] body = new byte[Integer.parseInt(length)];
        readMsgBody(inputStream, body);
        //handle msg
//        System.out.printf("on MSG subject: %s, sid: %s, replyTo: %s, bodyLength: %d,\n", subject, sid, replyTo, body.length);
        Long _sid = Long.parseLong(sid);
        msgEmitters.get(_sid).onNext(new MSG(subject, _sid, replyTo, body));
//        msgSubject.onNext(new MSG(subject, Long.parseLong(sid), replyTo, body));
    }

    private void readMsgBody(InputStream inputStream, byte[] body) throws Exception {
        int offset = max - min;
        int length = body.length;
        if (offset < length) {
            //copy first part
            System.arraycopy(buf, min, body, 0, offset);
            min = max;
            //read rest body
            while (offset < length) {
                int r = inputStream.read(body, offset, length - offset);
                if (r == -1)
                    throw new Exception("read -1");
                offset += r;
            }
        } else {
            //just copy
            System.arraycopy(buf, min, body, 0, length);
            min += length;
        }
//        System.out.printf(".....body is %s.....\n",new String(body));
    }

}
