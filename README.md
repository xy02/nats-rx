# client-rx
> It's a simple NATS client based on RxJava.

## Install
```gradle
repositories {
    ...
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xy02:nats-rx:0.2.0'
}
```
## Usage
Connect:
```java
    Client client = new Client("127.0.0.1");
```
Disconnect:
```java
    client.close();
```
Subscribe message:
```java
    client.subscribeMsg("test")
            .doOnNext(msg -> System.out.println(msg.getSubject() + ", body length:" + msg.getBody().length))
            .subscribe(msg -> {},err->{},()->System.out.println("subscribeMsg onComplete"));
```
Publish message:
```java
    Msg testMsg = new Msg("test", "hello".getBytes());
    client.publish(testMsg)
            .subscribe(()
```
Ping:
```java
    client.ping(3, TimeUnit.SECONDS)
            .subscribe(()
```
