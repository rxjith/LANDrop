package com.landrop.net;

import com.landrop.model.PeerDevice;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    public void start(String deviceName) {
        running = true;
        try {
            groupAddress = InetAddress.getByName(MULTICAST_GROUP);
            socket = new MulticastSocket(MULTICAST_PORT);
            socket.joinGroup(new java.net.InetSocketAddress(groupAddress, MULTICAST_PORT), null); // Join the multicast group to listen for discovery messages

            // Start the listener thread for incoming discovery messages
            new Thread(this::listenLoop, "Discovery-Listener").start();
            // Start the broadcaster thread to announce this device's presence
            new Thread(() -> broadcastLoop(deviceName), "Presence-Broadcaster").start();
            // Start the cleanup thread to remove inactive peers from the registry
            new Thread(this::cleanupLoop, "Discovery-Cleaner").start();


        } catch (Exception e) {
            e.printStackTrace();
    }
    }

    private void listenLoop() {
        byte[] buffer = new byte[2048]; // Buffer for incoming UDP packets
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                if (stealthMode) {
                    continue; // Skip processing if in stealth mode
                }
                
                String payload = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                InetAddress senderIp = packet.getAddress();

                if(payload.startsWith("DISCOVER:")) {
                    //Format: DISCOVER:<device_name>
                    String hostname = payload.substring(9); // Extract the device name from the payload
                    String ipKey = senderIp.getHostAddress(); 
                    
                    activePeers.compute(ipKey, (k, existingPeer) -> {
                        if (existingPeer == null) {
                            // New peer discovered, add to the registry
                            return new PeerDevice(senderIp, hostname);
                        } else {
                            // Existing peer, update its last seen timestamp
                            existingPeer.updateLastSeen();
                            return existingPeer;
                        }
                    });
                } else if(payload.startsWith("CHAT:")) {

                    if(chatMessageListener != null){
                        chatMessageListener.accept(payload.substring(5)); // Pass the chat message to the listener callback
                    }

                }
            } catch (Exception e){
                if(!running) {
                    break; // Exit the loop if the service is no longer running
                }
            }
        }
    }

    private void broadcastLoop(String deviceName) {
        while (running){
            try {
                if (!stealthMode) {
                    String beacon = "DISCOVER:" + deviceName; // Construct the discovery message
                    byte[] data = beacon.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, MULTICAST_PORT); // Create a UDP packet for broadcasting
                    socket.send(packet); // Send the discovery packet to the multicast group
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
        if (stealthMode || !running) return;
        try {
            String payload = "CHAT:" + deviceName + ": " + message;
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, MULTICAST_PORT);    // Create a UDP packet for the chat message
            socket.send(packet);
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

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) { // Close the multicast socket to stop receiving and sending messages
            socket.close();
        }
    }
}


