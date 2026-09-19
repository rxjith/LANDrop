package com.landrop.net;

import java.io.BufferedReader; // For reading input from the socket
import java.io.InputStreamReader; // For reading input from the socket
import java.io.PrintWriter; // For sending output to the socket
import java.net.Socket; // For socket communication
import java.net.ServerSocket; // For creating a server socket
import java.util.function.Consumer; // For handling incoming messages with a callback
import java.util.logging.Level; // For logging purposes
import java.util.logging.Logger; // For logging purposes

public class ChatManager {
    private static final Logger LOGGER = Logger.getLogger(ChatManager.class.getName()); // Logger for logging messages
    private static final int CHAT_PORT = 52146; // Port number for chat communication

    private ServerSocket serverSocket; // Server socket for listening to incoming connections
    private volatile boolean running = false; // Flag to indicate if the chat manager is running
    private final Consumer<String> onMessageReceived; // Callback for handling incoming messages

    public ChatManager(Consumer<String> onMessageReceived){
        this.onMessageReceived = onMessageReceived; // Initialize the callback for incoming messages
    }

    public void startServer() {
        running = true; // Set the running flag to true
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(CHAT_PORT); // Create a server socket on the specified port
                while (running) {
                    Socket client = serverSocket.accept(); // Accept incoming client connections
                    new Thread(() -> handleIncoming(client)).start(); // Handle each client connection in a new thread
                }
            } catch (Exception e) {
                if (running) {
                    LOGGER.log(Level.SEVERE, "Chat server encountered an error: ", e); // Log any errors encountered while running the server
                }
            }
        }, "ChatServerThread").start(); // Start the server thread
    }

    private void handleIncoming(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (onMessageReceived != null) {
                    onMessageReceived.accept(line); // Invoke the callback with the received message
                }
            }
        } catch (Exception ignored) {
        }                
    } 

    public static void sendMessageAsync(String targetIp, String senderHost, String message) {
        new Thread(() -> {
            try (Socket socket = new Socket(targetIp, CHAT_PORT);
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                writer.println("[" + senderHost + "] " + message); // Send the message to the target IP
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to send message to " + targetIp + ": ", e); // Log any errors encountered while sending the message
            }
        }, "ChatClient-Worker").start();
    }

    public void stop() {
        running = false; // Set the running flag to false
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Close the server socket if it's open
            }
        } catch (Exception ignored) {
            // Ignore any exceptions that occur while closing the server socket
        }
    }

}
