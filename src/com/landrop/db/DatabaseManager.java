package com.landrop.db;

import com.landrop.model.TransferMetadata;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static String dbUrl;

    private DatabaseManager() {
        // Prevent instantiation
    }

    private static synchronized String getDbUrl() {
        if (dbUrl == null) {
            String userHome = System.getProperty("user.home");
            File appDir = new File(userHome, ".landrop");
            if (!appDir.exists() && !appDir.mkdirs()) {
                LOGGER.log(Level.SEVERE, "Failed to create application directory at {0}", appDir.getAbsolutePath());
            }
            dbUrl = "jdbc:sqlite:" + new File(appDir, "landrop.db").getAbsolutePath();
        }
        return dbUrl;
    }
    
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "SQLite JDBC Driver missing from classpath!", e);
            throw new IllegalStateException("SQLite JDBC Driver missing. Ensure sqlite-jdbc dependency is present.", e);
        }       
        return DriverManager.getConnection(getDbUrl());
    }
    
    public static void initializeDatabase() {
        String createTransfersTable = "CREATE TABLE IF NOT EXISTS pending_transfers (" +
                "transfer_id TEXT PRIMARY KEY, " +
                "file_name TEXT NOT NULL, " +
                "total_bytes INTEGER NOT NULL, " +
                "bytes_transferred INTEGER NOT NULL, " +
                "sha256_hash TEXT NOT NULL, " +
                "peer_ip TEXT NOT NULL, " +
                "status TEXT NOT NULL, " +
                "last_updated INTEGER NOT NULL);";
        
        String createPeersTable = "CREATE TABLE IF NOT EXISTS trusted_peers (" +
                "ip_address TEXT PRIMARY KEY, " +
                "hostname TEXT NOT NULL, " +
                "added_at INTEGER NOT NULL);";
        
        try (Connection conn = getConnection()) {
            // Set PRAGMA configurations outside of explicit transaction
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
                stmt.execute("PRAGMA busy_timeout = 5000;");
            }

            // Execute table creation inside transaction
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTransfersTable);
                stmt.execute(createPeersTable);
                conn.commit();
                LOGGER.info("SQLite database initialized successfully with WAL mode.");
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Failed to execute database initialization queries", e);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize SQLite database connection", e);
        }
    }

    public static class TransferRecord {
        public String transferId;
        public String fileName;
        public long totalBytes;
        public long bytesTransferred;
        public String sha256Hash;
        public String peerIp;
        public String status;
        public long lastUpdated;

        public TransferRecord(String transferId, String fileName, long totalBytes, long bytesTransferred,
                            String sha256Hash, String peerIp, String status, long lastUpdated) {
            this.transferId = transferId;
            this.fileName = fileName;
            this.totalBytes = totalBytes;
            this.bytesTransferred = bytesTransferred;
            this.sha256Hash = sha256Hash;
            this.peerIp = peerIp;
            this.status = status;
            this.lastUpdated = lastUpdated;
        }
    }
    
    public static void saveCheckpoint(TransferMetadata metadata, String status) {
        if (metadata == null || metadata.getTransferId() == null) {
            LOGGER.warning("Attempted to save checkpoint with invalid or null TransferMetadata.");
            return;
        }

        String sql = "INSERT INTO pending_transfers " +
                     "(transfer_id, file_name, total_bytes, bytes_transferred, sha256_hash, peer_ip, status, last_updated) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(transfer_id) DO UPDATE SET " +
                     "file_name = excluded.file_name, " +
                     "total_bytes = excluded.total_bytes, " +
                     "bytes_transferred = excluded.bytes_transferred, " +
                     "sha256_hash = excluded.sha256_hash, " +
                     "peer_ip = excluded.peer_ip, " +
                     "status = excluded.status, " +
                     "last_updated = excluded.last_updated;";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, metadata.getTransferId());
            pstmt.setString(2, metadata.getFileName());
            pstmt.setLong(3, metadata.getTotalSizeBytes());
            pstmt.setLong(4, metadata.getBytesTransferred());
            pstmt.setString(5, metadata.getSha256Hash());
            pstmt.setString(6, metadata.getPeerIp());
            pstmt.setString(7, status != null ? status : "UNKNOWN");
            pstmt.setLong(8, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error saving transfer checkpoint for ID: " + metadata.getTransferId(), e);
        }
    }
    
    public static long getResumeOffset(String transferId) {
        if (transferId == null || transferId.trim().isEmpty()) {
            return 0L;
        }

        String sql = "SELECT bytes_transferred FROM pending_transfers WHERE transfer_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, transferId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("bytes_transferred");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error reading resume offset for ID: " + transferId, e);
        } 
        
        return 0L;
    }
    
    public static java.util.Set<String> getAllTrustedIpAddresses() {
        java.util.Set<String> trustedIps = new java.util.HashSet<>();
        String sql = "SELECT ip_address FROM trusted_peers";
        try (Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                trustedIps.add(rs.getString("ip_address"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving trusted peers list", e);
        }
        return trustedIps;
    }

    public static boolean isPeerTrusted(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return false;
        }

        String sql = "SELECT 1 FROM trusted_peers WHERE ip_address = ?";
        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ipAddress);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking trust status for IP: " + ipAddress, e);
            return false;
        }
    }

    public static void setPeerTrust(String ipAddress, String hostname, boolean trusted) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            LOGGER.warning("Attempted to set peer trust for invalid IP address.");
            return;
        }

        if (trusted) {
            String sql = "INSERT INTO trusted_peers (ip_address, hostname, added_at) VALUES (?, ?, ?) " +
                        "ON CONFLICT(ip_address) DO UPDATE SET hostname = excluded.hostname;";
            try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, ipAddress);
                pstmt.setString(2, hostname);
                pstmt.setLong(3, System.currentTimeMillis());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error saving trusted peer: " + ipAddress, e);
            }
        } else {
            String sql = "DELETE FROM trusted_peers WHERE ip_address = ?";
            try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, ipAddress);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error removing trusted peer: " + ipAddress, e);
            }
        }
    }

    public static java.util.List<TransferRecord> getTransferHistory() {
        java.util.List<TransferRecord> list = new java.util.ArrayList<>();
        String sql = "SELECT transfer_id, file_name, total_bytes, bytes_transferred, sha256_hash, peer_ip, status, last_updated " +
                    "FROM pending_transfers ORDER BY last_updated DESC";

        try (Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new TransferRecord(
                    rs.getString("transfer_id"),
                    rs.getString("file_name"),
                    rs.getLong("total_bytes"),
                    rs.getLong("bytes_transferred"),
                    rs.getString("sha256_hash"),
                    rs.getString("peer_ip"),
                    rs.getString("status"),
                    rs.getLong("last_updated")
                ));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error reading transfer history", e);
        }
        return list;
    }
}