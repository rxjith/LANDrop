package com.landrop.util;

public class TransferMetrics {

    private long startTime;

    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    public String getFormattedProgress(long bytesTransferred, long totalBytes) {
        long elapsedMs = System.currentTimeMillis() - startTime;
        if (elapsedMs <= 0) elapsedMs = 1;

        double bytesPerSec = (bytesTransferred * 1000.0) / elapsedMs;
        double speedMBs = bytesPerSec / (1024.0 * 1024.0);

        long remainingBytes = totalBytes - bytesTransferred;
        long etaSeconds = bytesPerSec > 0 ? (long) (remainingBytes / bytesPerSec) : 0;

        return String.format("%.2f MB/s — ETA: %02dm:%02ds", 
                speedMBs, 
                etaSeconds / 60, 
                etaSeconds % 60);
    }
}