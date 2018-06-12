# client-rx
> It's a simple NATS client based on RxJava.

## Install
```gradle
repositories {
    ...
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xy02:nats-rx:0.3.0'
}
```
## Usage
```java
    //connect
    IConnection nc = new Connection(new Options());
    Disposable ncd = nc.connect().subscribe();

    //auto reconnect
    Disposable ncd = nc.connect()
            .retryWhen(x -> x.delay(1, TimeUnit.SECONDS))
            .subscribe();

    //disconnect
    ncd.dispose();

    //subscribe message
    Disposable sd = nc.subscribeMsg("test")
            .doOnNext(msg -> System.out.println(msg.getSubject() + ", body length:" + msg.getBody().length))
            .subscribe(msg -> {},err->{},()->System.out.println("subscribeMsg onComplete"))

    //unsubscribe
    sd.dispose();

    //publish message
    Msg msg = new Msg("test", "hello".getBytes());
    client.publish(msg);

    //ping
    nc.ping(TimeUnit.MICROSECONDS)
        .doOnSuccess(t->System.out.println("ping μs:"+t))
        .subscribe();

```
