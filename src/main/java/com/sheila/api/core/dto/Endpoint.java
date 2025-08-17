package com.sheila.api.core.dto;

/** UDP istemcisini tanımlayan basit değer tipi. */
public class Endpoint {
    private final String ip;
    private final int port;

    public Endpoint(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
    public String getIp() { return ip; }
    public int getPort() { return port; }
    @Override public String toString() { return ip + ":" + port; }
}