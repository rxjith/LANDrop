package com.landrop.net;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

public class FileTransferClient {
    public static void sendFile(InetAddress targetIp, int targetPort, File file) throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("Target file not found: " + file.getAbsolutePath());
        }

        try (Socket socket = new Socket(targetIp, targetPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(file)) {

            // Header metadata
            out.writeUTF(file.getName());
            out.writeLong(file.length());

            // Stream payload
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        }
    }
}
    