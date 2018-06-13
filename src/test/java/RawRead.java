import io.reactivex.Observable;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class RawRead {
    public static void main(String[] args) {
        try {
            Socket socket = new Socket("127.0.0.1", 4222);
            OutputStream os = new BufferedOutputStream(socket.getOutputStream(), 1024 * 64);
//            InputStream is = new BufferedInputStream(socket.getInputStream(),1024*64);
            InputStream is = socket.getInputStream();
            byte[] BUFFER_CONNECT = "CONNECT {\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"name\":\"\",\"lang\":\"java\",\"version\":\"0.2.3\",\"protocol\":0}\r\n".getBytes();
            os.write(BUFFER_CONNECT);
            byte[] sub = "SUB sub1 1\r\n".getBytes();
            os.write(sub);
            os.flush();
            int size = "MSG sub1 1 5\r\nhello\r\n".getBytes().length;
            int pre = "{\"server_id\":\"ev5HIpmsJeOkJu14AgvTdA\",\"version\":\"1.1.1\",\"git_commit\":\"\",\"go\":\"go1.10.2\",\"host\":\"0.0.0.0\",\"port\":4222,\"auth_required\":false,\"tls_required\":false,\"tls_verify\":false,\"max_payload\":1048576}".getBytes().length;
            Observable.interval(0,1,TimeUnit.SECONDS)
                    .doOnNext(X-> System.out.printf("ops: %d/s, times:%d\n", (read-pre)/size/(X+1), times))
                    .subscribe();
            byte[] buf = new byte[1024*64];

            Observable.create(emitter -> {
                while (true) {
                    read+=is.read(buf);
                    times++;
//                    emitter.onNext(read);
                }
            })
//                    .doOnNext(x->System.out.println(++times))
                    .subscribe();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    static long read =0;
    static long times =0;
}
