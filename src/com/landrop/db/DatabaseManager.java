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
}