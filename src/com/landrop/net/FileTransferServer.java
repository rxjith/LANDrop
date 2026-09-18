package com.landrop.net;

import com.landrop.db.DatabaseManager;
import com.landrop.model.TransferMetadata;

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
            String peerIp = socket.getInetAddress().getHostAddress();
            try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())
            ) {
                String transferId = in.readUTF();
                String fileName = in.readUTF();
                long fileSize = in.readLong();
                String sha256 = in.readUTF();

                TransferMetadata metadata = new TransferMetadata(transferId, fileName, fileSize, sha256, peerIp);

                // Fetch previous offset if transfer was interrupted
                long resumeOffset = DatabaseManager.getResumeOffset(transferId);
                metadata.setBytesTransferred(resumeOffset);

                out.writeLong(resumeOffset);
                out.flush();

                File targetFile = new File(downloadDir, fileName);
                boolean append = resumeOffset > 0 && targetFile.exists();

                try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                    if (append) {
                        raf.seek(resumeOffset);
                    } else {
                        raf.setLength(0);
                    }

                    byte[] buffer = new byte[65536];
                    long totalRead = resumeOffset;
                    int read;
                    long lastCheckpoint = System.currentTimeMillis();

                    while (totalRead < fileSize && (read = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        raf.write(buffer, 0, read);
                        totalRead += read;
                        metadata.setBytesTransferred(totalRead);

                        if (System.currentTimeMillis() - lastCheckpoint > 1000) {
                            DatabaseManager.saveCheckpoint(metadata, "IN_PROGRESS");
                            lastCheckpoint = System.currentTimeMillis();
                        }
                    }

                    if (totalRead == fileSize) {
                        DatabaseManager.saveCheckpoint(metadata, "COMPLETED");
                        out.writeUTF("SUCCESS");
                        System.out.println("\n[FILE RECEIVED] " + fileName + " saved to " + targetFile.getAbsolutePath());
                    } else {
                        DatabaseManager.saveCheckpoint(metadata, "INTERRUPTED");
                        out.writeUTF("FAILED");
                    }
                }
                out.flush();
                System.out.print("> ");
            } catch (IOException e) {
                System.err.println("[FILE ERROR] Transfer failed from " + peerIp + ": " + e.getMessage());
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