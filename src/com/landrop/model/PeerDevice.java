package com.landrop.model;

import java.net.InetAddress;

public class PeerDevice {
    private final InetAddress ipAddress;
    private final String hostname;
    private int port;
    private long lastSeenTimestamp;
    private boolean isTrusted;
    
    public PeerDevice(InetAddress ipAddress, String hostname) {
        this(ipAddress, hostname, 0);
    }

    public PeerDevice(InetAddress ipAddress, String hostname, int port) {
        this.ipAddress = ipAddress;
        this.hostname = hostname;
        this.port = port;
        this.lastSeenTimestamp = System.currentTimeMillis();
        this.isTrusted = false;
    }
    
    public InetAddress getIpAddress() { 
        return ipAddress;
    }
    
    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
    
    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }
    
    public void updateLastSeen() {
        this.lastSeenTimestamp = System.currentTimeMillis();
    }
    
    public boolean isTrusted() {
        return isTrusted;
    }
    
    public void setTrusted(boolean trusted) {
        isTrusted = trusted;
    }

    @Override
    public String toString() {
        return hostname + " (" + ipAddress.getHostAddress() + ":" + port + ")";
    }
}