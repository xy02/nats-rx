# client-rx
> It's a simple NATS client based on RxJava.

## Install
```gradle
repositories {
    ...
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xy02:nats-rx:0.4.2'
}
```
## Usage
```java
    //connect
    IConnection nc = new Connection(new Options());

    //subscribe message
    Disposable sd = nc.subscribeMsg("test")
            .doOnNext(msg -> System.out.println(msg.getSubject() + ", body length:" + msg.getBody().length))
            .subscribe(msg -> {},err->{},()->System.out.println("subscribeMsg onComplete"))

    //unsubscribe
    sd.dispose();

    //publish message
    MSG msg = new MSG("test", "hello".getBytes());
    client.publish(msg);

    //request
    nc.request("foo", "bar".getBytes(), 1, TimeUnit.SECONDS)
            .doOnSuccess(msg -> System.out.printf("msg length: %d\n", msg.getBody().length))
            .subscribe();

    //ping
    nc.ping()
        .doOnSuccess(t->System.out.println("ping ms:"+t))
        .subscribe();

    //disconnect
    nc.close();

```
