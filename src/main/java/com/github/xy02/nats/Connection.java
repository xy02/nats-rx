package com.github.xy02.nats;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class Connection implements IConnection {

    public Connection() {
        this(new Options());
    }

    public Connection(Options options) {
        this.options = options;
    }

    @Override
    public Observable<String> connect() {
        return Single.<InputStream>create(emitter -> {
            System.out.printf("create, tid:%d\n", Thread.currentThread().getId());
            socket = new Socket(options.getHost(), options.getPort());
            OutputStream os = new BufferedOutputStream(socket.getOutputStream(), 1024 * 64);
            os.write(BUFFER_CONNECT);
            osSubject.onNext(os);
            emitter.onSuccess(socket.getInputStream());
        }).subscribeOn(Schedulers.io())
                .flatMapObservable(this::parseMessage)
//                .flatMap(this::readMessage)
//                .flatMapCompletable(message -> message.handle(this))
                .doOnDispose(() -> {
                    socket.close();
                });
    }

    @Override
    public Observable<MSG> subscribeMsg(String subject) {
        return subscribeMsg(subject, "");
    }

    @Override
    public synchronized Observable<MSG> subscribeMsg(String subject, String queue) {
        final int _sid = ++sid;
        byte[] subMessage = ("SUB " + subject + " " + queue + " " + _sid + "\r\n").getBytes();
        byte[] unsubMessage = ("UNSUB " + _sid + "\r\n").getBytes();
        Disposable d = osSubject.doOnNext(os -> outputSubject.onNext(subMessage))
                .doOnNext(x -> System.out.println("write"))
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .doOnDispose(() -> {
                    System.out.printf("subscribeMsg doOnDispose, tid:%d", Thread.currentThread().getId());
                    singleOutputStream
                            .doOnSuccess(os -> outputSubject.onNext(unsubMessage))
                            .subscribe(os -> {
                            }, Throwable::printStackTrace);
                })
//                .subscribeOn(Schedulers.io())
                .subscribe();
        //need improve
        return msgSubject.filter(msg -> msg.getSubject().equals(subject) && msg.getSid() == _sid)
                .doOnDispose(d::dispose);
    }

    @Override
    public Completable publish(MSG msg) {
        int bodyLength = msg.getBody().length;
        byte[] message = ("PUB " + msg.getSubject() + " " + msg.getReplyTo() + " " + bodyLength + "\r\n").getBytes();
        byte[] data = ByteBuffer.allocate(message.length + bodyLength + 2).put(message).put(msg.getBody()).put(BUFFER_CRLF).array();
        return singleOutputStream
//                .doOnSuccess(x-> System.out.printf("write tid:%d\n", Thread.currentThread().getId()))
                .doOnSuccess(os -> os.write(data))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .toCompletable();
    }

    @Override
    public Completable ping() {
        return null;
    }

    private final static byte[] BUFFER_CONNECT = "CONNECT {\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"name\":\"\",\"lang\":\"java\",\"version\":\"0.2.3\",\"protocol\":0}\r\n".getBytes();
    private final static byte[] BUFFER_PONG = "PONG\r\n".getBytes();
    private final static byte[] BUFFER_CRLF = "\r\n".getBytes();
    private final static String TYPE_INFO = "INFO";
    private final static String TYPE_MSG = "MSG";
    private final static String TYPE_PING = "PING";
    private final static String TYPE_PONG = "PONG";
    private final static String TYPE_OK = "+OK";
    private final static String TYPE_ERR = "-ERR";
    private int sid;
    private Options options;
    private Socket socket;
    private byte[] buf = new byte[1024 * 64];
    String[] fragmentArr = new String[10];
    private Subject<OutputStream> osSubject = BehaviorSubject.create();
    private Single<OutputStream> singleOutputStream = osSubject.take(1).singleOrError();
    Subject<Boolean> pongSubject = PublishSubject.create();
    Subject<MSG> msgSubject = PublishSubject.create();
    private Subject<byte[]> outputSubject = PublishSubject.create();
    private Disposable outputDisposable = outputSubject
            .flatMapSingle(data -> singleOutputStream.doOnSuccess(outputStream -> outputStream.write(data)))
            .retry()
//            .subscribeOn(Schedulers.io())
            .subscribe();
    private Disposable flushOutputDisposable = Observable.interval(0, 100, TimeUnit.MICROSECONDS)
            .flatMapSingle(x -> singleOutputStream.doOnSuccess(OutputStream::flush))
            .retry()
//            .subscribeOn(Schedulers.io())
            .subscribe();

    private Observable<String> parseMessage(InputStream inputStream) {
        return Observable.create(emitter -> {
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
                            emitter.onNext(messageType);
                            switch (messageType) {
                                case TYPE_INFO:
                                    //emitter.onNext(new INFO(list.get(1)));
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
                                    //emitter.onNext(new PONG());
                                    break;
                                case TYPE_OK:
                                    //emitter.onNext(new OK());
                                    break;
                                case TYPE_ERR:
                                    //emitter.onNext(new ERR(list));
                                    break;
                                default:
                                    throw new Exception("bad message type");
                            }
                            fragment = 0;
                        }
                        offset = i + 1;
                        continue;
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

//    private Subject<OutputStream> outputStreamSubject = BehaviorSubject.create();
//    private Single<OutputStream> singleOutputStream = outputStreamSubject.take(1).singleOrError();
//    private Subject<byte[]> outputSubject = PublishSubject.create();
//    private Disposable outputDisp = outputSubject.flatMapSingle().subscribe();
}
