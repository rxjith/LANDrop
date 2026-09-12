package com.landrop.model;

import java.net.InetAddress;

public class PeerDevice {
	private final InetAddress ipAddress;
    private final String hostname;
    private long lastSeenTimestamp;
    private boolean isTrusted;
    
    public PeerDevice(InetAddress ipAddress, String hostname) {
        this.ipAddress = ipAddress;
        this.hostname = hostname;
        this.lastSeenTimestamp = System.currentTimeMillis();
        this.isTrusted = false;
    }
    
    public InetAddress getIpAddress() { 
    	return ipAddress;
    }
    
    public String getHostname() {
    	return hostname;
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
}
