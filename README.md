# client-rx
> It's a simple NATS client based on RxJava.

## Install
```gradle
repositories {
    ...
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xy02:nats-rx:0.6.4'
}
```
## Usage
```java
    //connect
    IConnection nc = new Connection(new Options());

    //subscribe message
    Disposable sd = nc.subscribeMsg("test")
            .doOnNext(msg -> System.out.printf("subject:%s, body length:%d \n", msg.getSubject(), msg.getBody().length))
            .subscribe(msg -> {},err->{},()->System.out.println("subscribeMsg onComplete"));

    //unsubscribe
    sd.dispose();

    //subscribe message on queue
    nc.subscribeMsg("test", "myQueue")
            .doOnNext(msg -> System.out.printf("subject:%s, body length:%d \n", msg.getSubject(), msg.getBody().length))
            .subscribe();

    //publish message
    MSG msg = new MSG("test", "hello".getBytes());
    client.publish(msg);

    //request
    nc.request("foo", "bar".getBytes(), 1, TimeUnit.SECONDS)
            .doOnSuccess(msg -> System.out.printf("msg length: %d\n", msg.getBody().length))
            .subscribe();

    //on reconnect
    nc.onReconnect()
            .doOnNext(x->  System.out.printf("!!!on reconnect %d\n",x))
            .subscribe();

    //ping
    nc.ping()
        .doOnSuccess(t->System.out.println("ping ms:"+t))
        .subscribe();

    //disconnect
    nc.close();

```
