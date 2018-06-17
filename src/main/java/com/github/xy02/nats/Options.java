package com.github.xy02.nats;

public class Options {
    private String host = "localhost";
    private int port = 4222;
    private boolean tls = false;

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
}
