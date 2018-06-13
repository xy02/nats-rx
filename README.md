# client-rx
> It's a simple NATS client based on RxJava.

## Install
```gradle
repositories {
    ...
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xy02:nats-rx:0.3.1'
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
    Msg msg = new Msg("test", "hello".getBytes());
    client.publish(msg);

    //ping
    nc.ping()
        .doOnSuccess(t->System.out.println("ping ms:"+t))
        .subscribe();

    //disconnect
    nc.close();

```
