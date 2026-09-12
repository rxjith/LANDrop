package com.landrop.model;

public class TransferMetadata {
	private final String transferId;
    private final String fileName;
    private final long totalSizeBytes;
    private long bytesTransferred;
    private final String sha256Hash;
    private final String peerIp;
    
    public TransferMetadata(String transferId, String fileName, long totalSizeBytes, String sha256Hash, String peerIp) {
        this.transferId = transferId;
        this.fileName = fileName;
        this.totalSizeBytes = totalSizeBytes;
        this.bytesTransferred = 0;
        this.sha256Hash = sha256Hash;
        this.peerIp = peerIp;
    }
    
    public String getTransferId() {
    	return transferId;
    }
    
    public String getFileName() {
    	return fileName;
    }
    
    public long getTotalSizeBytes() {
    	return totalSizeBytes;
    }
    
    public long getBytesTransferred() {
    	return bytesTransferred;
    }
    
    public void setBytesTransferred(long bytesTransferred) {
    	this.bytesTransferred = bytesTransferred;
    }
    
    public String getSha256Hash() {
    	return sha256Hash;
    }
    
    public String getPeerIp() {
    	return peerIp;
    }
}
