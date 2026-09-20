package com.landrop.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

    // Calculates the SHA-256 hash of a file using 64 KB buffer streams.
    public static String calculateSHA256(File file) {
        if (file == null || !file.exists() || !file.isFile()) return "";

        try (FileInputStream fis = new FileInputStream(file)) {
            return calculateSHA256(fis);
        } catch (IOException e) {
            System.err.println("[HashUtil] Calculation failed for file " + file.getName() + ": " + e.getMessage());
            return "";
        }
    }

    // Calculates the SHA-256 hash from an InputStream (useful for direct socket stream processing).
    public static String calculateSHA256(InputStream inputStream) {
        if (inputStream == null) return "";

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536]; // 64 KB buffer
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            System.err.println("[HashUtil] Stream calculation failed: " + e.getMessage());
            return "";
        }
    }

    // Calculates the SHA-256 hash of in-memory byte arrays (useful for clipboard text or chat payload verification).
    public static String calculateSHA256(byte[] data) {
        if (data == null || data.length == 0) return "";

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("[HashUtil] SHA-256 algorithm missing: " + e.getMessage());
            return "";
        }
    }

    // Verifies if a file matches an expected SHA-256 hash (case-insensitive).
    public static boolean verifySHA256(File file, String expectedHash) {
        if (expectedHash == null || expectedHash.trim().isEmpty()) return false;
        String actualHash = calculateSHA256(file);
        return !actualHash.isEmpty() && actualHash.equalsIgnoreCase(expectedHash.trim());
    }

    // Helper to convert a byte array into a lowercase hexadecimal string.
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}