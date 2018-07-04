package com.github.xy02.nats;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

public class Options {

    static final String REQUEST_PREFIX = "r.";

    //host of gnatsd server
    private String host = "localhost";

    public String getHost() {
        return host;
    }

    public Options setHost(String host) {
        if (host == null || host.isEmpty())
            return this;
        this.host = host;
        return this;
    }

    //port of gnatsd server
    private int port = 4222;

    public int getPort() {
        return port;
    }

    public Options setPort(int port) {
        this.port = port;
        return this;
    }

    //use tls
    private boolean tls = false;

    public boolean isTls() {
        return tls;
    }

    public Options setTls(boolean tls) {
        this.tls = tls;
        return this;
    }

    //Scheduler for reading input data
    private Scheduler readScheduler = Schedulers.io();

    public Scheduler getReadScheduler() {
        return readScheduler;
    }

    public Options setReadScheduler(Scheduler readScheduler) {
        this.readScheduler = readScheduler;
        return this;
    }

    //reconnect interval in second
    private int reconnectInterval = 1;

    public int getReconnectInterval() {
        return reconnectInterval;
    }

    public Options setReconnectInterval(int reconnectInterval) {
        if (reconnectInterval < 0)
            return this;
        this.reconnectInterval = reconnectInterval;
        return this;
    }

    //interval of flushing output data in MICROSECONDS
    private int flushInterval = 500;

    public int getFlushInterval() {
        return flushInterval;
    }

    public Options setFlushInterval(int flushInterval) {
        if (flushInterval < 100)
            return this;
        this.flushInterval = flushInterval;
        return this;
    }

    //Scheduler for receiving subscribed message
    private Scheduler subScheduler = Schedulers.computation();

    public Scheduler getSubScheduler() {
        return subScheduler;
    }

    public Options setSubScheduler(Scheduler subScheduler) {
        this.subScheduler = subScheduler;
        return this;
    }

}
