package com.landrop.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatManager {
    private static final Logger LOGGER = Logger.getLogger(ChatManager.class.getName());
    private static final int CHAT_PORT = 52146;

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    
    private final BiConsumer<String, String> onMessageReceived;

    public ChatManager(BiConsumer<String, String> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
    }

    public void startServer() {
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(CHAT_PORT);
                while (running) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleIncoming(client)).start();
                }
            } catch (Exception e) {
                if (running) {
                    LOGGER.log(Level.SEVERE, "Chat server encountered an error: ", e);
                }
            }
        }, "ChatServerThread").start();
    }

    private void handleIncoming(Socket socket) {
        String senderIp = socket.getInetAddress().getHostAddress();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (onMessageReceived != null) {
                    onMessageReceived.accept(senderIp, line);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void sendMessageAsync(String targetIp, String senderHost, String message) {
        new Thread(() -> {
            try (Socket socket = new Socket(targetIp, CHAT_PORT);
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                writer.println("[" + senderHost + "] " + message);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to send message to " + targetIp + ": ", e);
            }
        }, "ChatClient-Worker").start();
    }

    // NEW: Sends raw strings directly without formatting a sender header tag
    public static void sendRawMessageAsync(String targetIp, String rawPayload) {
        new Thread(() -> {
            try (Socket socket = new Socket(targetIp, CHAT_PORT);
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                writer.println(rawPayload);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to send raw payload to " + targetIp + ": ", e);
            }
        }, "ChatClient-RawWorker").start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        }
    }
}