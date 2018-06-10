package com.github.xy02.nats;

public class Options {
    private String host = "localhost";
    private int port = 4222;

    public String getHost() {
        return host;
    }

    public Options setHost(String host) {
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
}
