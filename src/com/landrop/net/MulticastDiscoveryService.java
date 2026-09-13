package com.landrop.net;

import com.landrop.model.PeerDevice;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class MulticastDiscoveryService {
    public static final String MULTICAST_GROUP = "230.0.0.1"; 
    public static final int MULTICAST_PORT = 4446; 
    private static final long PEER_TIMEOUT_MS = 10000; 

    private final Map<String, PeerDevice> activePeers = new ConcurrentHashMap<>(); 
    private MulticastSocket socket; 
    private InetAddress groupAddress; 
    private volatile boolean running = false; 
    private volatile boolean stealthMode = false; 
    private Consumer<String> chatMessageListener; 

    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);
    private final List<NetworkInterface> joinedInterfaces = new CopyOnWriteArrayList<>();
    private SocketAddress groupSocketAddress;
    private int localTcpPort = 0;

    private Thread listenerThread;
    private Thread broadcasterThread;
    private Thread cleanerThread;

    public void start(String deviceName) {
        start(deviceName, 0); 
    }

    public synchronized void start(String deviceName, int tcpPort) {
        if (running) return; 
        this.running = true;
        this.localTcpPort = tcpPort;

        try {
            groupAddress = InetAddress.getByName(MULTICAST_GROUP); 
            groupSocketAddress = new InetSocketAddress(groupAddress, MULTICAST_PORT); 
            
            socket = new MulticastSocket((SocketAddress) null);
            socket.setReuseAddress(true);
            try {
                socket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
            } catch (Exception ignored) {}
            socket.bind(new InetSocketAddress(MULTICAST_PORT));
            socket.setTimeToLive(4);

            try {
                socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false); 
            } catch (Exception ignored) {}

            joinActiveInterfaces(); 

            listenerThread = new Thread(this::listenLoop, "Discovery-Listener"); 
            broadcasterThread = new Thread(() -> broadcastLoop(deviceName), "Presence-Broadcaster"); 
            cleanerThread = new Thread(this::cleanupLoop, "Discovery-Cleaner"); 

            listenerThread.setDaemon(true);
            broadcasterThread.setDaemon(true);
            cleanerThread.setDaemon(true);

            listenerThread.start();
            broadcasterThread.start();
            cleanerThread.start();
        
        } catch (Exception e) {
            running = false;
            if (socket != null) {
                socket.close();
                socket = null;
            }
            joinedInterfaces.clear();
            e.printStackTrace();
        }
    }

    private void joinActiveInterfaces() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); 
            while (interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback() || !nic.supportsMulticast()) continue; 

                String display = nic.getDisplayName().toLowerCase();
                String name = nic.getName().toLowerCase(); 
                if (display.contains("virtual") || display.contains("vmware") || display.contains("wsl") || name.startsWith("docker")) {
                    continue; 
                }

                try {
                    socket.joinGroup(groupSocketAddress, nic); 
                    joinedInterfaces.add(nic); 
                } catch (IOException ignored) {} 
            }

            if (joinedInterfaces.isEmpty()) {
                try {
                    socket.joinGroup(groupSocketAddress, null);
                } catch (IOException | IllegalArgumentException ignored) {}
            }
        } catch (SocketException e) {
            System.err.println("[Discovery] Failed to enumerate network interfaces: " + e.getMessage());
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[2048]; 
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                InetAddress senderIp = packet.getAddress();

                if (stealthMode) {
                    continue; 
                }

                if (senderIp.isLoopbackAddress() || isLocalAddress(senderIp)) {
                    continue; 
                }

                String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim(); 

                if (payload.startsWith("DISCOVER:")) {
                    String[] parts = payload.split(":"); 
                    String senderInstanceId = "";
                    String hostname = "";
                    int parsedPort = 0;

                    if (parts.length >= 4) {
                        senderInstanceId = parts[1];    
                        hostname = parts[2];            
                        try {
                            parsedPort = Integer.parseInt(parts[3]);
                        } catch (NumberFormatException ignored) {}
                    } else if (parts.length == 2) {       
                        hostname = parts[1];            
                    }

                    // Ignore messages from the same instance
                    if (this.instanceId.equals(senderInstanceId)) {
                        continue; 
                    }

                    // FIXED: Always key activePeers by sender IP address
                    String ipKey = senderIp.getHostAddress(); 
                    final String resolvedHostname = hostname;
                    final int tcpPort = parsedPort;

                    activePeers.compute(ipKey, (k, existingPeer) -> {
                        if (existingPeer == null) {
                            return new PeerDevice(senderIp, resolvedHostname, tcpPort);
                        } else {
                            existingPeer.setPort(tcpPort); 
                            existingPeer.updateLastSeen();
                            return existingPeer;
                        }
                    });
                } else if (payload.startsWith("CHAT:")) {
                    String[] chatParts = payload.split(":", 4);
                    if (chatParts.length >= 4) {
                        String chatSenderId = chatParts[1];
                        if (this.instanceId.equals(chatSenderId)) {
                            continue;
                        }
                        if (chatMessageListener != null) {
                            chatMessageListener.accept(chatParts[2] + ": " + chatParts[3]); 
                        }
                    } else if (chatMessageListener != null) {
                        chatMessageListener.accept(payload.substring(5)); 
                    }

                } else if (payload.startsWith("BYE:")) {
                    String[] byeParts = payload.split(":");
                    if (byeParts.length >= 2) {
                        activePeers.remove(byeParts[1]);
                    }
                    activePeers.remove(senderIp.getHostAddress());
                }
            } catch (SocketException e) {
                if (!running) break;
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    private boolean isLocalAddress(InetAddress addr) {
        try {
            return NetworkInterface.getByInetAddress(addr) != null; 
        } catch (SocketException e) {
            return false; 
        }
    }

    private void broadcastLoop(String deviceName) {
        while (running) {
            try {
                if (!stealthMode && socket != null && !socket.isClosed()) {
                    String beacon = String.format("DISCOVER:%s:%s:%d", instanceId, deviceName, localTcpPort); 
                    byte[] data = beacon.getBytes(StandardCharsets.UTF_8);
                    sendMulticastPacket(data);
                }
                Thread.sleep(3000); 
            } catch (InterruptedException e) {
                break; 
            } catch (Exception e) {
                if (!running) break; 
            }
        }
    }

    private void cleanupLoop() {
        while (running) {
            try {
                Thread.sleep(3000); 
                long now = System.currentTimeMillis();
                activePeers.entrySet().removeIf(entry -> 
                    (now - entry.getValue().getLastSeenTimestamp()) > PEER_TIMEOUT_MS
                );
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public void sendSubnetChat(String deviceName, String message) {
        if (stealthMode || !running || socket == null || socket.isClosed()) return;
        try {
            String payload = "CHAT:" + instanceId + ":" + deviceName + ":" + message;
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            sendMulticastPacket(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper method to eliminate duplicate multicast sending logic
    private void sendMulticastPacket(byte[] data) {
        if (joinedInterfaces.isEmpty()) {
            try {
                DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, MULTICAST_PORT);
                socket.send(packet);
            } catch (IOException ignored) {}
        } else {
            for (NetworkInterface nic : joinedInterfaces) {
                try {
                    socket.setNetworkInterface(nic);
                    DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, MULTICAST_PORT);
                    socket.send(packet);
                } catch (IOException ignored) {}
            }
        }
    }

    public void setStealthMode(boolean enabled) { 
        this.stealthMode = enabled;
    }

    public boolean isStealthMode() { 
        return stealthMode;
    }

    public Map<String, PeerDevice> getActivePeers() { 
        return activePeers;
    }

    public void setChatMessageListener(Consumer<String> listener) { 
        this.chatMessageListener = listener;
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;

        if (broadcasterThread != null) broadcasterThread.interrupt();
        if (cleanerThread != null) cleanerThread.interrupt();

        try {
            if (socket != null && !socket.isClosed()) {
                String byePayload = "BYE:" + instanceId;
                byte[] data = byePayload.getBytes(StandardCharsets.UTF_8);
                sendMulticastPacket(data);
            }
        } catch (Exception ignored) {}

        if (socket != null && !socket.isClosed()) {
            for (NetworkInterface nic : joinedInterfaces) { 
                try {
                    socket.leaveGroup(groupSocketAddress, nic);
                } catch (Exception ignored) {}
            }
            joinedInterfaces.clear();
            socket.close(); 
        }
        activePeers.clear();
    }
}