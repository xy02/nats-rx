package com.github.xy02.nats;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.omg.PortableInterceptor.INACTIVE;

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
    //min bound of buf
    private int min = 0;
    //max bound of buf
    private int max = 0;

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
//        os.write(BUFFER_CONNECT);
        OutputStream outputStream = new BufferedOutputStream(os, 1024 * 64);
        InputStream inputStream = socket.getInputStream();
        System.out.printf("connect on :%s\n", Thread.currentThread().getName());
        readData3(inputStream)
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

    private final static byte M = (byte) 'M';
    private final static byte S = (byte) 'S';
    private final static byte G = (byte) 'G';
    private final static byte I = (byte) 'I';
    private final static byte N = (byte) 'N';
    private final static byte F = (byte) 'F';
    private final static byte O = (byte) 'O';
    private final static byte P = (byte) 'P';
    private final static byte K = (byte) 'K';
    private final static byte E = (byte) 'E';
    private final static byte R = (byte) 'R';
    private final static byte CR = (byte) '\r';
    private final static byte LF = (byte) '\n';
    private final static byte PLUS = (byte) '+';
    private final static byte MINUS = (byte) '-';
    private final static byte SPACE = (byte) ' ';

    private Observable<Long> readData2(InputStream inputStream) {
        return Observable.<Long>create(emitter -> {
            while (true) {
                while (min + 5 < max) {
                    if (buf[min] == M && buf[++min] == S && buf[++min] == G && buf[++min] == SPACE) {
                        ++min;
                        readMSG(inputStream);
                    } else if (buf[min] == CR || buf[min] == LF) {
                        ++min;
                    } else if (buf[min] == I && buf[++min] == N && buf[++min] == F && buf[++min] == O && buf[++min] == SPACE) {
                        ++min;
                        readINFO(inputStream);
                    } else if (buf[min] == P && buf[++min] == I && buf[++min] == N && buf[++min] == G && buf[++min] == CR) {
                        ++min;
                        outputSubject.onNext(BUFFER_PONG);
                    } else if (buf[min] == P && buf[++min] == O && buf[++min] == N && buf[++min] == G && buf[++min] == CR) {
                        ++min;
                        onPongSubject.onNext(true);
                    } else if (buf[min] == PLUS && buf[++min] == O && buf[++min] == K && buf[++min] == CR && buf[++min] == LF) {
                        ++min;
                        System.out.println(TYPE_OK);
                    } else if (buf[min] == MINUS && buf[++min] == E && buf[++min] == R && buf[++min] == R && buf[++min] == SPACE) {
                        ++min;
                        readERR(inputStream);
                    } else {
                        throw new Exception("bad message type");
                    }
                }
                int remain = max - min;
                if (remain > 0)
                    System.arraycopy(buf, min, buf, 0, remain);
                int read = inputStream.read(buf, remain, buf.length - remain);
                if (read == -1)
                    throw new Exception("read -1");
                max = remain + read;
                min = 0;
                Thread.yield();
            }
        }).doOnError(Throwable::printStackTrace);
    }

    private Observable<Long> readData3(InputStream inputStream) {
        return Observable.<Long>create(emitter -> {
            while (true) {
                String messageType = readString(inputStream);
                switch (messageType){
                    case TYPE_MSG:
                        readMSG(inputStream);
                        break;
                    case TYPE_INFO:
                        String json = readString(inputStream);
                        System.out.println(json);
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
                        String err =readString(inputStream);
                        System.out.println(err);
                        break;
                    default:
                        throw new Exception("bad message type");
                }
                while(min< max){
                    byte b = buf[min];
                    if (  b == CR || b== LF||b== SPACE ){
                        min++;
                    }else{
                        throw new Exception("bad message");
                    }
                }
                if(min <max && buf[min] == CR){
                    min++;
                }
                if (min <max && buf[min] == LF){
                    min++;
                }
                if (min == max){
                    max = inputStream.read(buf);
                    min = 0;
                }
            }
        }).doOnError(Throwable::printStackTrace);
    }

    //return true if the end char is CR
    private String readString(InputStream inputStream) throws Exception {
        int offset = min;
        while (true) {
            while (min < max) {
                if (buf[min] == SPACE || buf[min] == CR) {
                    String str = new String(buf, offset, min - offset);
//                    System.out.println(str);
                    min++;
//                    System.out.printf("c:%d, min:%d\n",buf[min], min);
                    return str;
                }
                min++;
            }
            //move rest of buf to the start
            min = max - offset;
            if (min > 0)
                System.arraycopy(buf, offset, buf, 0, min);
            int read = inputStream.read(buf, min, buf.length - min);
            if (read == -1)
                throw new Exception("read -1");
            max = min + read;
            offset = 0;
        }
    }

    private void readMSG(InputStream inputStream) throws Exception {
        int fragment = 0;
        int offset = min;
        //header loop
        while (true) {
            while (min < max) {
                byte b = buf[min];
                if (b == SPACE || b == CR) {
                    fragmentArr[fragment] = new String(buf, offset, min - offset);
                    fragment++;
                    offset = ++min;
                    continue;
                }
                if (b == LF) {
                    String subject = fragmentArr[0];
                    String sid = fragmentArr[1];
                    String replyTo;
                    if (fragment == 3) {
                        replyTo = "";
                    } else {
                        replyTo = fragmentArr[2];
                    }
                    int length = Integer.parseInt(fragmentArr[fragment - 1]);
                    byte[] body = new byte[length];
                    min++;
                    readMsgBody(inputStream, body);
                    //handle
                    msgSubject.onNext(new MSG(subject, Integer.parseInt(sid), replyTo, body));
                    return;
                }
                if (b < 0)
                    throw new Exception("bad message");
                ++min;
            }
            //move rest of buf to the start
            min = max - offset;
            if (min > 0)
                System.arraycopy(buf, offset, buf, 0, min);
            int read = inputStream.read(buf, min, buf.length - min);
            if (read == -1)
                throw new Exception("read -1");
            max = min + read;
            offset = 0;
        }
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
    }

    private void readINFO(InputStream inputStream) throws Exception {
        int offset = min;
        while (true) {
            while (min < max) {
                if (buf[min] == CR) {
                    String json = new String(buf, offset, min - offset);
                    System.out.println(json);
                    min++;
                    return;
                }
                min++;
            }
            //move rest of buf to the start
            min = max - offset;
            if (min > 0)
                System.arraycopy(buf, offset, buf, 0, min);
            int read = inputStream.read(buf, min, buf.length - min);
            if (read == -1)
                throw new Exception("read -1");
            max = min + read;
            offset = 0;
        }
    }

    private void readERR(InputStream inputStream) throws Exception {
        int offset = min;
        while (true) {
            while (min < max) {
                if (buf[min] == CR) {
                    String json = new String(buf, offset, min - offset);
                    System.out.println(json);
                    min++;
                    return;
                }
                min++;
            }
            //move rest of buf to the start
            min = max - offset;
            if (min > 0)
                System.arraycopy(buf, offset, buf, 0, min);
            int read = inputStream.read(buf, min, buf.length - min);
            if (read == -1)
                throw new Exception("read -1");
            max = min + read;
            offset = 0;
        }
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
                Thread.yield();
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
