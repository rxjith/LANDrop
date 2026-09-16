package com.landrop;

import com.landrop.net.FileTransferClient;
import com.landrop.net.FileTransferServer;
import com.landrop.net.MulticastDiscoveryService;

import java.io.File;
import java.net.InetAddress;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=================================================");
        System.out.println("          LANDrop Multicast Testbed              ");
        System.out.println("=================================================");

        System.out.print("Enter device name for this node [default: Abhijit-PC]: ");
        String nameInput = scanner.nextLine().trim();
        final String LOCAL_DEVICE_NAME = nameInput.isEmpty() ? "Abhijit-PC" : nameInput;

        System.out.print("Enter local TCP port to advertise [default: 52145]: ");
        String portInput = scanner.nextLine().trim();
        int tcpPort = portInput.isEmpty() ? 52145 : Integer.parseInt(portInput);

        MulticastDiscoveryService discoveryService = new MulticastDiscoveryService();

        // 1. Initialize and start the background TCP file receiving server
        FileTransferServer fileServer = new FileTransferServer(tcpPort, "downloads");
        try {
            fileServer.start();
            System.out.println("[TCP FILE SERVER] Listening for incoming files on port " + tcpPort);
        } catch (Exception e) {
            System.err.println("[TCP FILE SERVER ERROR] Failed to start file server: " + e.getMessage());
        }

        // Listen for incoming subnet chat messages
        discoveryService.setChatMessageListener(incomingMsg -> {
            System.out.println("\n[SUBNET CHAT] " + incomingMsg);
            System.out.print("> ");
        });

        System.out.println("\nStarting discovery service for [" + LOCAL_DEVICE_NAME + "] advertising TCP port " + tcpPort + "...");
        discoveryService.start(LOCAL_DEVICE_NAME, tcpPort);
        System.out.println("Service online on 230.0.0.1:4446.");
        System.out.println("-------------------------------------------------");
        System.out.println("Commands:");
        System.out.println("  /peers                - Show currently active devices");
        System.out.println("  /send <ip> <filepath> - Stream file over TCP to peer IP");
        System.out.println("  /msg <text>           - Broadcast chat message across interfaces");
        System.out.println("  /stealth              - Toggle stealth mode on/off");
        System.out.println("  /help                 - Show available commands");
        System.out.println("  /exit                 - Leave chat, broadcast BYE, and shutdown");
        System.out.println("-------------------------------------------------");

        boolean active = true;
        System.out.print("> ");

        while (active) {
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                System.out.print("> ");
                continue;
            }

            if (input.equalsIgnoreCase("/exit")) {
                active = false;
            } else if (input.equalsIgnoreCase("/peers")) {
                System.out.println("\n--- Active Remote Peers (" + discoveryService.getActivePeers().size() + ") ---");
                if (discoveryService.getActivePeers().isEmpty()) {
                    System.out.println("No peers discovered yet. Ensure both devices share the same Wi-Fi/subnet.");
                } else {
                    discoveryService.getActivePeers().forEach((key, peer) -> {
                        long secondsAgo = (System.currentTimeMillis() - peer.getLastSeenTimestamp()) / 1000;
                        System.out.printf("  * [%s] Host: %s | IP: %s | seen %ds ago%n",
                                key,
                                peer.getHostname(),
                                peer.getIpAddress().getHostAddress(),
                                secondsAgo
                        );
                    });
                }
                System.out.println("-------------------------------------------------\n");
            } else if (input.startsWith("/send ")) {
                String[] parts = input.split(" ", 3);
                if (parts.length < 3) {
                    System.out.println("Usage: /send <peer-ip> <file-path>");
                } else {
                    String targetIp = parts[1];
                    File targetFile = new File(parts[2].replace("\"", ""));

                    new Thread(() -> {
                        try {
                            System.out.println("[FILE] Sending " + targetFile.getName() + " to " + targetIp + ":" + tcpPort + "...");
                            FileTransferClient.sendFile(InetAddress.getByName(targetIp), tcpPort, targetFile);
                            System.out.println("[FILE] Transfer complete: " + targetFile.getName());
                        } catch (Exception e) {
                            System.err.println("[FILE ERROR] Failed sending file: " + e.getMessage());
                        }
                        System.out.print("> ");
                    }).start();
                }
            } else if (input.equalsIgnoreCase("/stealth")) {
                boolean nextState = !discoveryService.isStealthMode();
                discoveryService.setStealthMode(nextState);
                System.out.println("[Status] Stealth mode is now: " + (nextState ? "ENABLED (Hidden)" : "DISABLED (Visible)"));
            } else if (input.startsWith("/msg ")) {
                String message = input.substring(5).trim();
                if (!message.isEmpty()) {
                    discoveryService.sendSubnetChat(LOCAL_DEVICE_NAME, message);
                    System.out.println("[Sent]: " + message);
                }
            } else if (input.equalsIgnoreCase("/help")) {
                System.out.println("\nAvailable commands:");
                System.out.println("  /peers                - Show currently active devices");
                System.out.println("  /send <ip> <filepath> - Stream file over TCP to peer IP");
                System.out.println("  /msg <text>           - Broadcast chat message across interfaces");
                System.out.println("  /stealth              - Toggle broadcast suppression on/off");
                System.out.println("  /help                 - Show this menu");
                System.out.println("  /exit                 - Leave chat, broadcast BYE, and shutdown\n");
            } else {
                discoveryService.sendSubnetChat(LOCAL_DEVICE_NAME, input);
            }

            if (active) {
                System.out.print("> ");
            }
        }

        System.out.println("\nStopping discovery service and file server...");
        fileServer.stop();
        discoveryService.stop();
        scanner.close();
        System.out.println("Disconnected.");
        System.exit(0);
    }
}