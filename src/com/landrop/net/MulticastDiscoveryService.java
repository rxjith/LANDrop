package com.landrop.net;

import com.landrop.model.PeerDevice;
import com.landrop.util.AppConfig;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class MulticastDiscoveryService {
    // Multicast IP and Port agreed upon across the LAN
    public static final String MULTICAST_GROUP = "230.0.0.1"; 
    public static final int MULTICAST_PORT = 4446; 
    private static final long PEER_TIMEOUT_MS = 10000; // Drop peers silent for >10s

    // Thread-safe map storing active peers indexed by IP address
    private final Map<String, PeerDevice> activePeers = new ConcurrentHashMap<>(); 
    
    private MulticastSocket socket; 
    private InetAddress groupAddress; 
    private SocketAddress groupSocketAddress;
    
    private volatile boolean running = false; 
    private volatile boolean stealthMode = false; 
    private Consumer<String> chatMessageListener; 

    // Unique ID for this instance so we ignore our own broadcasts
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);
    private final List<NetworkInterface> joinedInterfaces = new CopyOnWriteArrayList<>();
    private int localTcpPort = 0;

    // Background threads
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
        this.stealthMode = AppConfig.isStealthMode();

        try {
            groupAddress = InetAddress.getByName(MULTICAST_GROUP); 
            groupSocketAddress = new InetSocketAddress(groupAddress, MULTICAST_PORT); 
            
            // 1. Setup Multicast Socket with address re-use
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

            // 2. Bind to real physical network interfaces (filter out VPNs/VMs)
            joinActiveInterfaces(); 

            // 3. Start background worker threads
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

    // Filter out WSL, VirtualBox, Hyper-V, Docker, and VPN interfaces
    private void joinActiveInterfaces() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); 
            while (interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback() || !nic.supportsMulticast()) continue; 

                String display = nic.getDisplayName().toLowerCase();
                String name = nic.getName().toLowerCase(); 
                
                if (display.contains("virtual") || display.contains("vmware") || 
                    display.contains("wsl") || display.contains("hyper-v") || 
                    display.contains("vethernet") || display.contains("zerotier") ||
                    display.contains("tailscale") || name.startsWith("docker") || name.startsWith("veth")) {
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

    // LISTENER THREAD: Listens continuously for UDP packets on 230.0.0.1:4446
    private void listenLoop() {
        byte[] buffer = new byte[2048]; 
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                InetAddress senderIp = packet.getAddress();

                // Ignore packets sent from our own local machine
                if (senderIp.isLoopbackAddress() || isLocalAddress(senderIp)) {
                    continue; 
                }

                String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim(); 

                // CASE A: Someone is announcing presence ("DISCOVER:<instance_id>:<hostname>:<tcp_port>")
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

                    // Ignore if the discovery packet came from our own instance
                    if (this.instanceId.equals(senderInstanceId)) {
                        continue; 
                    }

                    String ipKey = senderIp.getHostAddress(); 
                    final String resolvedHostname = hostname;
                    final int tcpPort = parsedPort;

                    // Insert or update peer in thread-safe map
                    activePeers.compute(ipKey, (k, existingPeer) -> {
                        if (existingPeer == null) {
                            return new PeerDevice(senderIp, resolvedHostname, tcpPort);
                        } else {
                            existingPeer.setPort(tcpPort); 
                            existingPeer.updateLastSeen();
                            return existingPeer;
                        }
                    });

                // CASE B: Someone sent a subnet chat message ("CHAT:<instance_id>:<sender>:<message>")
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

                // CASE C: Someone is shutting down ("BYE:<instance_id>")
                } else if (payload.startsWith("BYE:")) {
                    // Remove peer from map using their IP address key
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

    // BROADCASTER THREAD: Sends a "DISCOVER" multicast packet every 3 seconds
    private void broadcastLoop(String deviceName) {
        while (running) {
            try {
                boolean currentStealth = stealthMode || AppConfig.isStealthMode();
                if (!currentStealth && socket != null && !socket.isClosed()) {
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

    // CLEANER THREAD: Purges peers that haven't sent a beacon in >10 seconds
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

    // Sends a direct multicast text message to everyone on the subnet
    public void sendSubnetChat(String deviceName, String message) {
        if (stealthMode || AppConfig.isStealthMode() || !running || socket == null || socket.isClosed()) return;
        try {
            String payload = "CHAT:" + instanceId + ":" + deviceName + ":" + message;
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            sendMulticastPacket(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper method to write UDP datagrams out to all joined physical interfaces
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
        // Going ghost: send a BYE first so peers drop us immediately instead of waiting for a timeout
        if (enabled && !isStealthMode() && running && socket != null && !socket.isClosed()) {
            try {
                sendMulticastPacket(("BYE:" + instanceId).getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
        this.stealthMode = enabled;
        AppConfig.setStealthMode(enabled);
    }

    public boolean isStealthMode() { 
        return stealthMode || AppConfig.isStealthMode();
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

        // Broadcast disconnect packet before closing
        try {
            if (socket != null && !socket.isClosed() && !isStealthMode()) {
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