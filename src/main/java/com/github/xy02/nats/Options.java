package com.github.xy02.nats;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

public class Options {
    private String host = "localhost";
    private int port = 4222;
    private boolean tls = false;

    private Scheduler readScheduler = Schedulers.newThread();
    //second
    private int reconnectInterval = 1;

    public String getHost() {
        return host;
    }

    public Options setHost(String host) {
        if (host == null || host.isEmpty())
            return this;
        this.host = host;
        return this;
    }

    public Options setPort(int port) {
        this.port = port;
        return this;
    }

    public int getPort() {
        return port;
    }

    public int getReconnectInterval() {
        return reconnectInterval;
    }

    public Options setReconnectInterval(int reconnectInterval) {
        if (reconnectInterval < 0)
            return this;
        this.reconnectInterval = reconnectInterval;
        return this;
    }

    public boolean isTls() {
        return tls;
    }

    public Options setTls(boolean tls) {
        this.tls = tls;
        return this;
    }

    public Scheduler getReadScheduler() {
        return readScheduler;
    }

    public Options setReadScheduler(Scheduler readScheduler) {
        this.readScheduler = readScheduler;
        return this;
    }
}
