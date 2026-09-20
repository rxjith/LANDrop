package com.landrop.net;

import com.landrop.db.DatabaseManager;
import com.landrop.model.TransferMetadata;
import com.landrop.util.AppConfig;
import com.landrop.util.CryptoUtil;
import com.landrop.util.HashUtil;
import com.landrop.util.ZipUtil;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.UUID;

public class FileTransferClient {

    public interface ProgressCallback {
        void onProgress(long bytesSent, long totalBytes);
    }

    public static boolean sendFile(InetAddress targetIp, int targetPort, File file, ProgressCallback callback) throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("Target file not found: " + file.getAbsolutePath());
        }

        String hash = HashUtil.calculateSHA256(file);
        String transferId = UUID.nameUUIDFromBytes((file.getName() + "_" + file.length()).getBytes()).toString();
        TransferMetadata metadata = new TransferMetadata(transferId, file.getName(), file.length(), hash, targetIp.getHostAddress());

        try (Socket socket = new Socket(targetIp, targetPort);
            // Wrap the socket output stream with AES Encryption
            OutputStream encryptedOut = CryptoUtil.wrapEncryptedOutput(socket.getOutputStream());
            DataOutputStream out = new DataOutputStream(encryptedOut);
            
            // Wrap the socket input stream with AES Decryption
            InputStream decryptedIn = CryptoUtil.wrapDecryptedInput(socket.getInputStream());
            DataInputStream in = new DataInputStream(decryptedIn);
            
            FileInputStream fis = new FileInputStream(file)) {

            // Header handshake: ID | Name | Size | Hash
            out.writeUTF(transferId);
            out.writeUTF(file.getName());
            out.writeLong(file.length());
            out.writeUTF(hash);
            out.flush();

            // Read resume offset or acceptance status from receiver
            long offset = in.readLong();
            if (offset == -1L) {
                DatabaseManager.saveCheckpoint(metadata, "DECLINED");
                throw new IOException("Transfer request was declined by the remote peer.");
            }

            if (offset > 0 && offset < file.length()) {
                fis.skip(offset);
                metadata.setBytesTransferred(offset);
            }

            DatabaseManager.saveCheckpoint(metadata, "IN_PROGRESS");

            byte[] buffer = new byte[65536]; // 64 KB stream chunk
            long totalRead = metadata.getBytesTransferred();
            int bytesRead;
            long lastCheckpoint = System.currentTimeMillis();

            long transferStartTime = System.currentTimeMillis();
            long bytesSentSinceStart = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                bytesSentSinceStart += bytesRead;
                metadata.setBytesTransferred(totalRead);

                if (callback != null) {
                    callback.onProgress(totalRead, file.length());
                }

                // Bandwidth Throttling Logic
                int speedLimitKbps = AppConfig.getBandwidthLimitKbps();
                if (speedLimitKbps > 0) {
                    long expectedMs = (bytesSentSinceStart * 1000L) / ((long) speedLimitKbps * 1024L);
                    long actualMs = System.currentTimeMillis() - transferStartTime;
                    if (expectedMs > actualMs) {
                        try {
                            Thread.sleep(expectedMs - actualMs);
                        } catch (InterruptedException ignored) {}
                    }
                }

                // SQLite Checkpoint sync (every 1 second)
                if (System.currentTimeMillis() - lastCheckpoint > 1000) {
                    DatabaseManager.saveCheckpoint(metadata, "IN_PROGRESS");
                    lastCheckpoint = System.currentTimeMillis();
                }
            }
            out.flush();

            String response = in.readUTF();
            boolean success = "SUCCESS".equalsIgnoreCase(response);
            DatabaseManager.saveCheckpoint(metadata, success ? "COMPLETED" : "FAILED");
            return success;
        } catch (IOException e) {
            DatabaseManager.saveCheckpoint(metadata, "INTERRUPTED");
            throw e;
        }
    }

    public interface BatchProgressCallback {
        void onFileStart(String fileName, int currentIndex, int totalFiles);
        void onFileProgress(long bytesSent, long totalBytes);
        void onBatchComplete(int successCount, int failedCount);
    }

    public static void sendBatch(InetAddress targetIp, int targetPort, List<File> files, BatchProgressCallback callback) {
        new Thread(() -> {
            int successCount = 0;
            int failedCount = 0;

            for (int i = 0; i < files.size(); i++) {
                File rawFile = files.get(i);
                File fileToSend = rawFile;
                boolean isTempZip = false;

                try {
                    if (rawFile.isDirectory()) {
                        fileToSend = ZipUtil.zipFolder(rawFile);
                        isTempZip = true;
                    }

                    final String displayName = rawFile.getName() + (rawFile.isDirectory() ? " (Folder)" : "");
                    if (callback != null) {
                        callback.onFileStart(displayName, i + 1, files.size());
                    }

                    boolean success = sendFile(targetIp, targetPort, fileToSend, (sent, total) -> {
                        if (callback != null) {
                            callback.onFileProgress(sent, total);
                        }
                    });

                    if (success) successCount++;
                    else failedCount++;

                } catch (Exception e) {
                    failedCount++;
                } finally {
                    if (isTempZip && fileToSend != null && fileToSend.exists()) {
                        fileToSend.delete();
                    }
                }
            }

            if (callback != null) {
                callback.onBatchComplete(successCount, failedCount);
            }
        }, "BatchFileSenderThread").start();
    }
}