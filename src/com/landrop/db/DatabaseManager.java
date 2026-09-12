package com.landrop.db;

import com.landrop.model.TransferMetadata;
import java.sql.*;

public class DatabaseManager {
	private static final String DB_URL = "jdbc:sqlite:landrop.db";
	
	public static Connection getConnection() throws SQLException {
		try  {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			System.err.println("SQLite Driver Class missing from Build Path!");
		}		
		return DriverManager.getConnection(DB_URL);
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
				"last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
		
		String createPeersTable = "CREATE TABLE IF NOT EXISTS trusted_peers (" +
				"ip_address TEXT PRIMARY KEY, " +
				"hostname TEXT NOT NULL, " +
				"added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
		
		try (Connection conn = getConnection();
			 Statement stmt = conn.createStatement()) {
			stmt.execute(createTransfersTable);
			stmt.execute(createPeersTable);
			System.out.println("SQLite tables checked/created successfully.");
		} catch (SQLException e) {
			System.err.println("Failed to initialize SQLite database: " + e.getMessage());
		}
	}
	
	public static void saveCheckpoint(TransferMetadata metadata, String status) {
		String sql = "INSERT INTO pending_transfers (transfer_id, file_name, total_bytes, bytes_transferred, sha256_hash, peer_ip, status) " +
					 "VALUES (?, ?, ?, ?, ?, ?, ?) " +
					 "ON CONFLICT(transfer_id) DO UPDATE SET " +
					 "bytes_transferred = excluded.bytes_transferred, " +
					 "status = excluded.status, " +
					 "last_updated = CURRENT_TIMESTAMP;";
		
		try (Connection conn = getConnection();
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, metadata.getTransferId());
			pstmt.setString(2, metadata.getFileName());
			pstmt.setLong(3, metadata.getTotalSizeBytes());
			pstmt.setLong(4, metadata.getBytesTransferred());
			pstmt.setString(5, metadata.getSha256Hash());
			pstmt.setString(6, metadata.getPeerIp());
			pstmt.setString(7, status);
			pstmt.executeUpdate();
		} catch (SQLException e) {
			System.err.println("Error saving checkpoint: " + e.getMessage());
		}
	}
	
	public static long getResumeOffset(String transferId) {
		String sql = "SELECT bytes_transferred FROM pending_transfers WHERE transfer_id = ?";
		
		try (Connection conn = getConnection();
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1,  transferId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				return rs.getLong("bytes_transferred");
			}
		} catch (SQLException e) {
			System.err.println("Error reading resume offset: " + e.getMessage());
		} return 0;
	}
}
