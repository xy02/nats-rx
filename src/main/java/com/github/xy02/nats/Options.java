package com.github.xy02.nats;

public class Options {
    private int port = 4222;

    public Options setPort(int port) {
        this.port = port;
        return this;
    }

    public int getPort() {
        return port;
    }
}
