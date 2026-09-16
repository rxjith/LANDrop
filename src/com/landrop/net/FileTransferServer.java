package com.landrop.net;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransferServer {
    private final int port;
    private final String downloadDir;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public FileTransferServer(int port, String downloadDir) {
        this.port = port;
        this.downloadDir = downloadDir;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        new Thread(() -> {
            new File(downloadDir).mkdirs();
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    handleIncomingTransfer(socket);
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        }, "LANDrop-TCP-Receiver").start();
    }

    private void handleIncomingTransfer(Socket socket) {
        new Thread(() -> {
            try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
                String fileName = in.readUTF();
                long fileSize = in.readLong();

                File targetFile = new File(downloadDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    long totalRead = 0;
                    int read;

                    while (totalRead < fileSize && (read = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        fos.write(buffer, 0, read);
                        totalRead += read;
                    }
                }
                System.out.println("\n[FILE RECEIVED] Saved to " + targetFile.getAbsolutePath());
                System.out.print("> ");
            } catch (IOException e) {
                System.err.println("[FILE ERROR] Failed receiving: " + e.getMessage());
            }
        }).start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }
}