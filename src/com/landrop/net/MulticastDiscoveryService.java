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
    public static final String MULTICAST_GROUP = "230.0.0.1"; // Multicast IP used for LANDrop local peer discovery
    public static final int MULTICAST_PORT = 4446; // UDP port for LANDrop instance discovery broadcasts
    private static final long PEER_TIMEOUT_MS = 10000; // Time in milliseconds (here 10s) before an inactive peer is considered offline (10s)

    private final Map<String, PeerDevice> activePeers = new ConcurrentHashMap<>(); // Thread-safe registry of currently active peers indexed by identifier
    private MulticastSocket socket; // Multicast socket for sending and receiving discovery messages
    private InetAddress groupAddress; // Multicast group address for peer discovery
    private volatile boolean running = false; // Flag to control the discovery service's running state
    private volatile boolean stealthMode = false; // Flag to indicate if the service is in stealth mode (not broadcasting its presence)
    private Consumer<String> chatMessageListener; // Callback for handling incoming chat messages

    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);
    private final List<NetworkInterface> joinedInterfaces = new CopyOnWriteArrayList<>();
    private SocketAddress groupSocketAddress;
    private int localTcpPort = 0;

    private Thread listenerThread;
    private Thread broadcasterThread;
    private Thread cleanerThread;

    public void start(String deviceName){
        start(deviceName, 0);   // Start the discovery service with the specified device name and default TCP port (0 means any available port)
    }

    public synchronized void start(String deviceName, int tcpPort) {
        if (running) return; // Prevent starting the service if it's already running
        this.running = true;
        this.localTcpPort = tcpPort;

        try {
            groupAddress = InetAddress.getByName(MULTICAST_GROUP); // Resolve the multicast group address
            groupSocketAddress = new InetSocketAddress(groupAddress, MULTICAST_PORT); // Create a socket address for the multicast group
            
            socket = new MulticastSocket(MULTICAST_PORT); // Create a multicast socket bound to the specified port
            socket.setReuseAddress(true);
            try {
                socket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
            } catch (Exception ignored) {}
            socket.setTimeToLive(4);

            try {
                socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false); // Disable loopback for multicast packets
            } catch (Exception ignored) {}

            joinActiveInterfaces(); // Explicitly binds physical interfaces

            listenerThread = new Thread(this::listenLoop, "Discovery-Listener"); // Start a thread to listen for incoming discovery messages
            broadcasterThread = new Thread(() -> broadcastLoop(deviceName), "Presence-Broadcaster"); // Start a thread to broadcast discovery messages
            cleanerThread = new Thread(this::cleanupLoop, "Discovery-Cleaner"); // Start a thread to clean up inactive peers

            listenerThread.setDaemon(true);
            broadcasterThread.setDaemon(true);
            cleanerThread.setDaemon(true);

            listenerThread.start();
            broadcasterThread.start();
            cleanerThread.start();
        
        } catch (Exception e) {
            e.printStackTrace(); //
        }
    }

    private void joinActiveInterfaces() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); // Get all network interfaces on the machine
            while (interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback() || !nic.supportsMulticast()) continue; // Skip interfaces that are down, loopback, or don't support multicast

                String display = nic.getDisplayName().toLowerCase();
                String name = nic.getName().toLowerCase(); // Get the interface name and display name in lowercase for comparison
                if (display.contains("virtual") || display.contains("vmware") || display.contains("wsl") || name.startsWith("docker")){
                    continue; // Skip virtual, VMware, WSL, and Docker interfaces
                }

                try {
                    socket.joinGroup(groupSocketAddress, nic); // Join the multicast group on the selected interface
                    joinedInterfaces.add(nic); // Keep track of the joined interfaces
                } catch (IOException ignored){} // Ignore exceptions when joining the group on certain interfaces
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
        byte[] buffer = new byte[2048]; // Buffer for incoming UDP packets
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                InetAddress senderIp = packet.getAddress();

                if (stealthMode) {
                    continue; // Skip processing if in stealth mode
                }

                if (senderIp.isLoopbackAddress() || isLocalAddress(senderIp)) {
                    continue; // Ignore packets sent by this machine (loopback or local addresses)
                }

                String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim(); // Decode the incoming packet payload as a UTF-8 string

                if(payload.startsWith("DISCOVER:")){
                    String[] parts = payload.split(":"); // Split the payload into parts using ":" as the delimiter
                    String senderInstanceId = "";
                    String hostname = "";
                    int parsedPort = 0;

                    if(parts.length >= 4){
                        senderInstanceId = parts[1];    // Extract the instance ID from the payload
                        hostname = parts[2];            // Extract the hostname from the payload
                        try {
                            parsedPort = Integer.parseInt(parts[3]);
                        } catch (NumberFormatException ignored) {}
                    } else if(parts.length == 2){       // Handle older format without instance ID
                        hostname = parts[1];            // Extract the hostname from the payload
                    }

                    // Ignore messages from the same instance (loopback)
                    if (this.instanceId.equals(senderInstanceId)) {
                        continue; // Ignore messages from the same instance (loopback)
                    }
                    String ipkey = !senderInstanceId.isEmpty() ? senderInstanceId : senderIp.getHostAddress(); // Get the sender's IP address as a string
                    final String resolvedHostname = hostname;
                    final int tcpPort = parsedPort;

                    activePeers.compute(ipkey, (k, existingPeer) -> {
                        if (existingPeer == null) {
                            // New peer discovered with TCP port stored
                            return new PeerDevice(senderIp, resolvedHostname, tcpPort);
                        } else {
                            // Existing peer, refresh port if changed and update timestamp
                            existingPeer.setPort(tcpPort); 
                            existingPeer.updateLastSeen();
                            return existingPeer;
                        }
                    });
                } else if(payload.startsWith("CHAT:")) {
                    String[] chatParts = payload.split(":", 4);
                    if (chatParts.length >= 4) {
                        String chatSenderId = chatParts[1];
                        if (this.instanceId.equals(chatSenderId)) {
                            continue;
                        }
                        if (chatMessageListener != null) {
                            chatMessageListener.accept(chatParts[2] + ": " + chatParts[3]); // Pass the chat message to the listener callback
                        }
                    } else if(chatMessageListener != null){
                        chatMessageListener.accept(payload.substring(5)); // Pass the chat message to the listener callback
                    }

                } else if(payload.startsWith("BYE:")) {
                    String[] byeParts = payload.split(":");
                    if (byeParts.length >= 2) {
                        activePeers.remove(byeParts[1]);
                    }
                    activePeers.remove(senderIp.getHostAddress());
                }
            } catch (SocketException e) {
                if (!running) {
                    break;
                }
            } catch (Exception e){
                if(!running) {
                    break; // Exit the loop if the service is no longer running
                }
            }
        }
    }

     private boolean isLocalAddress(InetAddress addr) {
        try {
            return NetworkInterface.getByInetAddress(addr) != null; // Check if the address belongs to any of the local network interfaces
        } catch (SocketException e) {
            return false; // If an exception occurs, assume it's not a local address
        }
    }

    private void broadcastLoop(String deviceName) {
        while (running) {
            try {
                if (!stealthMode && socket != null && !socket.isClosed()) {
                    String beacon = String.format("DISCOVER:%s:%s:%d", instanceId, deviceName, localTcpPort);   // Construct the discovery beacon message with instance ID, device name, and local TCP port
                    byte[] data = beacon.getBytes(StandardCharsets.UTF_8);

                    if (joinedInterfaces.isEmpty()) {
                        DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, MULTICAST_PORT); // Create a UDP packet for broadcasting
                        socket.send(packet); // Send the discovery packet to the multicast group
                    } else {
                        for (NetworkInterface nic : joinedInterfaces) {
                            try {
                                socket.setNetworkInterface(nic);
                                DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, MULTICAST_PORT); // Create a UDP packet for broadcasting
                                socket.send(packet); // Send the discovery packet to the multicast group
                            } catch (IOException ignored) {}
                        }
                    }
                }
                Thread.sleep(3000); // Wait for 3 seconds before sending the next broadcast
            } catch (InterruptedException e) {
                break; // Exit the loop if interrupted
            } catch (Exception e) {
                if (!running) {
                    break; // Exit the loop if the service is no longer running
                }
            }
        }
    }

    private void cleanupLoop() {
        while (running) {
            try {
                Thread.sleep(3000); // Check for inactive peers every 3 seconds
                long now = System.currentTimeMillis();
                activePeers.entrySet().removeIf(entry -> 
                    (now - entry.getValue().getLastSeenTimestamp()) > PEER_TIMEOUT_MS
                );
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    //Sends a chat message to all peers in the multicast group
    public void sendSubnetChat(String deviceName, String message) {
        if (stealthMode || !running || socket == null || socket.isClosed()) return;
        try {
            String payload = "CHAT:" + instanceId + ":" + deviceName + ":" + message;
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);

            if (joinedInterfaces.isEmpty()) {
                DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, MULTICAST_PORT);    // Create a UDP packet for the chat message
                socket.send(packet);
            } else {
                for (NetworkInterface nic : joinedInterfaces) {
                    try {
                        socket.setNetworkInterface(nic);
                        DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, MULTICAST_PORT);    // Create a UDP packet for the chat message
                        socket.send(packet);
                    } catch (IOException ignored) {}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setStealthMode(boolean enabled) {  // Enable or disable stealth mode, which controls whether the service broadcasts its presence
        this.stealthMode = enabled;
    }

    public boolean isStealthMode() {    // Return the current stealth mode status
        return stealthMode;
    }

    public Map<String, PeerDevice> getActivePeers() {   // Return a thread-safe view of the currently active peers
        return activePeers;
    }

    public void setChatMessageListener(Consumer<String> listener) {     // Set a listener for incoming chat messages
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
                if (joinedInterfaces.isEmpty()) {
                    DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, MULTICAST_PORT);
                    socket.send(packet);
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
        } catch (Exception ignored) {}

        if (socket != null && !socket.isClosed()) {
            for (NetworkInterface nic : joinedInterfaces) { // Leave the multicast group on each joined interface
                try {
                    socket.leaveGroup(groupSocketAddress, nic);
                } catch (Exception ignored) {}
            }
            joinedInterfaces.clear();
            socket.close(); // Close the multicast socket to stop receiving and sending messages
        }
        activePeers.clear();
    }
}