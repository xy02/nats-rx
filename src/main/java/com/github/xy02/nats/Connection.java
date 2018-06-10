package com.github.xy02.nats;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
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
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Connection implements IConnection {

    public Connection(){
        this(new Options());
    }

    public Connection(Options options) {
        this.options = options;
    }

    @Override
    public Completable connect() {
        return Observable.<InputStream>create(emitter -> {
            System.out.printf("create, tid:%d\n", Thread.currentThread().getId());
            socket = new Socket(options.getHost(), options.getPort());
            OutputStream os = new BufferedOutputStream(socket.getOutputStream(), 1024 * 64);
//            os.write(BUFFER_CONNECT);
            osSubject.onNext(os);
            emitter.onNext(socket.getInputStream());
            emitter.onComplete();
        }).subscribeOn(Schedulers.io())
                .flatMap(this::readMessage)
                .flatMapCompletable(message -> message.handle(this))
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
        Disposable d = osSubject.doOnNext(os -> os.write(subMessage))
                .doOnNext(x->System.out.println("write"))
                .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
                .doOnDispose(() -> {
                    System.out.printf("subscribeMsg doOnDispose, tid:%d",Thread.currentThread().getId());
                    singleOutputStream
                            .doOnSuccess(os -> os.write(unsubMessage))
                            .subscribe(os -> {
                            }, Throwable::printStackTrace);
                })
                .subscribeOn(Schedulers.single())
                .subscribe();
        //need improve
        return msgSubject.filter(msg -> msg.getSubject().equals(subject) && msg.getSid() == _sid)
                .doOnDispose(d::dispose);
    }

    @Override
    public Completable publish(MSG msg) {
        return null;
    }

    @Override
    public Completable ping() {
        return null;
    }

    private final static byte[] BUFFER_CONNECT = "CONNECT {\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"name\":\"\",\"lang\":\"java\",\"version\":\"0.2.3\",\"protocol\":0}\r\n".getBytes();
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
    private Subject<OutputStream> osSubject = BehaviorSubject.create();
    Single<OutputStream> singleOutputStream = osSubject.take(1).singleOrError();
    Subject<Boolean> pongSubject = PublishSubject.create();
    Subject<MSG> msgSubject = PublishSubject.create();

    private Observable<Message> readMessage(InputStream inputStream) {
        return Observable.<Message>create(emitter -> {
            ArrayList<String> list = new ArrayList<>();
            int temp = 0;
            int read;
            while ((read = inputStream.read(buf, temp, buf.length - temp)) != -1) {
                int offset = 0;
                for (int i = 0; i < read; i++) {
                    byte b = buf[i];
                    if (b == 32) {
                        if (i != offset)
                            list.add(new String(buf, offset, i - offset));
                        offset = i + 1;
                        continue;
                    }
                    if (b == 13) {
                        if (i != offset)
                            list.add(new String(buf, offset, i - offset));
                        offset = i + 1;
                        switch (list.get(0)) {
                            case TYPE_INFO:
                                emitter.onNext(new INFO(list.get(1)));
                                break;
                            case TYPE_MSG:
                                offset = readMSG(list, inputStream, emitter, offset + 1);
                                i = offset - 1;
                                break;
                            case TYPE_PING:
                                emitter.onNext(new PING());
                                break;
                            case TYPE_PONG:
                                emitter.onNext(new PONG());
                                break;
                            case TYPE_OK:
                                emitter.onNext(new OK());
                                break;
                            case TYPE_ERR:
                                emitter.onNext(new ERR(list));
                                break;
                            default:
                                throw new Exception("bad message type");
                        }
                        list = new ArrayList<>();
                        continue;
                    }
                    if (b == 10) {
                        offset = i + 1;
                        continue;
                    }
                    if (b < 0)
                        throw new Exception("bad message");
                }
                if (offset == read)
                    continue;
                //move rest of buf to the start
                temp = read - offset;
                System.arraycopy(buf, offset, buf, 0, temp);
            }
        }).subscribeOn(Schedulers.newThread());
    }

    private int readMSG(ArrayList<String> list, InputStream inputStream, ObservableEmitter<Message> emitter, int offset) throws Exception {
        if (list.size() < 4)
            throw new Exception("wrong msg");
        String subject = list.get(1);
        String sid = list.get(2);
        String replyTo = "";
        int length;
        if (list.size() == 4)
            length = Integer.parseInt(list.get(3));
        else {
            replyTo = list.get(3);
            length = Integer.parseInt(list.get(4));
        }
        byte[] data = new byte[length];
        //first,copy from buf to data
        if (offset + length > buf.length) {
            int index = buf.length - offset;
            System.arraycopy(buf, offset, data, 0, index);
            offset = buf.length;
            //read rest data
            while (index < length) {
                int read = inputStream.read(data, index, length - index);
                if (read == -1)
                    throw new Exception("read -1");
                index += read;
            }
        } else {
            System.arraycopy(buf, offset, data, 0, length);
            offset += length;
        }
        emitter.onNext(new MSG(subject, Integer.parseInt(sid), replyTo, data));
        return offset;
    }

//    private Subject<OutputStream> outputStreamSubject = BehaviorSubject.create();
//    private Single<OutputStream> singleOutputStream = outputStreamSubject.take(1).singleOrError();
//    private Subject<byte[]> outputSubject = PublishSubject.create();
//    private Disposable outputDisp = outputSubject.flatMapSingle().subscribe();
}
