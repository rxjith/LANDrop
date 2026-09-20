package com.landrop.net;

import com.landrop.db.DatabaseManager;
import com.landrop.model.TransferMetadata;
import com.landrop.util.CryptoUtil;
import com.landrop.util.HashUtil;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransferServer {

    @FunctionalInterface
    public interface TransferAcceptanceListener {
        boolean onRequestTransfer(String peerIp, String fileName, long fileSize);
    }

    private final int port;
    private final String downloadDir;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private TransferAcceptanceListener acceptanceListener;

    public FileTransferServer(int port, String downloadDir) {
        this.port = port;
        this.downloadDir = downloadDir;
    }

    public void setAcceptanceListener(TransferAcceptanceListener listener) {
        this.acceptanceListener = listener;
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
                // Wrap socket input stream with AES Decryption
                InputStream decryptedIn = CryptoUtil.wrapDecryptedInput(socket.getInputStream());
                DataInputStream in = new DataInputStream(decryptedIn);
                
                // Wrap socket output stream with AES Encryption
                OutputStream encryptedOut = CryptoUtil.wrapEncryptedOutput(socket.getOutputStream());
                DataOutputStream out = new DataOutputStream(encryptedOut)
            ) {
                String transferId = in.readUTF();
                String fileName = in.readUTF();
                long fileSize = in.readLong();
                String sha256 = in.readUTF();

                // 1. Check if peer is trusted; if not, ask for permission
                boolean isTrusted = DatabaseManager.isPeerTrusted(peerIp);
                boolean accepted = isTrusted;

                if (!isTrusted && acceptanceListener != null) {
                    accepted = acceptanceListener.onRequestTransfer(peerIp, fileName, fileSize);
                }

                // 2. If rejected, signal -1L and abort
                if (!accepted) {
                    out.writeLong(-1L);
                    out.flush();
                    System.out.println("[FILE SERVER] Declined incoming transfer: " + fileName + " from " + peerIp);
                    return;
                }

                TransferMetadata metadata = new TransferMetadata(transferId, fileName, fileSize, sha256, peerIp);

                long resumeOffset = DatabaseManager.getResumeOffset(transferId);
                metadata.setBytesTransferred(resumeOffset);

                // Send actual resume offset back to client
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
                        // Verify Hash before confirming success
                        String calculatedHash = HashUtil.calculateSHA256(targetFile);
                        if (calculatedHash.equalsIgnoreCase(sha256)) {
                            DatabaseManager.saveCheckpoint(metadata, "COMPLETED");
                            out.writeUTF("SUCCESS");
                            System.out.println("\n[FILE RECEIVED & VERIFIED] " + fileName + " saved to " + targetFile.getAbsolutePath());
                        } else {
                            DatabaseManager.saveCheckpoint(metadata, "CORRUPTED");
                            out.writeUTF("HASH_MISMATCH");
                            System.err.println("[FILE ERROR] Corrupted download for " + fileName);
                        }
                    } else {
                        DatabaseManager.saveCheckpoint(metadata, "INTERRUPTED");
                        out.writeUTF("FAILED");
                    }
                }
                out.flush();
            } catch (Exception e) {
                System.err.println("[FILE ERROR] Encrypted transfer failed from " + peerIp + ": " + e.getMessage());
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