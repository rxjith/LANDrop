package com.landrop.ui;

import com.landrop.db.DatabaseManager;
import com.landrop.db.DatabaseManager.TransferRecord;
import com.landrop.net.FileTransferClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class TransferHistoryDialog extends JDialog {

    private final DefaultTableModel tableModel;
    private final JTable historyTable;
    private List<TransferRecord> records;
    private final int targetPort;
    private Timer autoRefreshTimer;

    public TransferHistoryDialog(Frame parent, int targetPort) {
        super(parent, "Transfer History & Resumes", true);
        this.targetPort = targetPort;

        setSize(850, 400);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        // Added "Direction" column
        String[] columns = {"File Name", "Peer IP", "Direction", "Progress", "Status", "Last Updated"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        historyTable = new JTable(tableModel);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(historyTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshBtn = new JButton("Refresh");
        JButton resumeBtn = new JButton("Resume Selected Sender File");
        JButton closeBtn = new JButton("Close");

        refreshBtn.addActionListener(e -> loadHistory());
        resumeBtn.addActionListener(e -> attemptResume());
        closeBtn.addActionListener(e -> dispose());

        bottomPanel.add(refreshBtn);
        bottomPanel.add(resumeBtn);
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        loadHistory();

        // Start background auto-refresh timer (every 1.5 seconds) to update progress in real-time
        autoRefreshTimer = new Timer(1500, e -> loadHistoryPreservingSelection());
        autoRefreshTimer.start();

        // Ensure timer stops when dialog closes
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopTimer();
            }
        });
    }

    private void loadHistory() {
        tableModel.setRowCount(0);
        records = DatabaseManager.getTransferHistory();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (TransferRecord r : records) {
            // Display transfer direction (Sent / Received)
            String direction = r.direction != null ? r.direction : "UNKNOWN";

            String progress = String.format("%.1f / %.1f MB",
                r.bytesTransferred / (1024.0 * 1024.0),
                r.totalBytes / (1024.0 * 1024.0));
            String date = sdf.format(new Date(r.lastUpdated));

            tableModel.addRow(new Object[]{r.fileName, r.peerIp, direction, progress, r.status, date});
        }
    }

    private void loadHistoryPreservingSelection() {
        int selectedRow = historyTable.getSelectedRow();
        loadHistory();
        if (selectedRow >= 0 && selectedRow < historyTable.getRowCount()) {
            historyTable.setRowSelectionInterval(selectedRow, selectedRow);
        }
    }

    private void stopTimer() {
        if (autoRefreshTimer != null && autoRefreshTimer.isRunning()) {
            autoRefreshTimer.stop();
        }
    }

    @Override
    public void dispose() {
        stopTimer();
        super.dispose();
    }

    private void attemptResume() {
        int selectedRow = historyTable.getSelectedRow();
        if (selectedRow < 0 || records == null || selectedRow >= records.size()) {
            JOptionPane.showMessageDialog(this, "Select a transfer record to resume.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        TransferRecord record = records.get(selectedRow);
        if ("COMPLETED".equalsIgnoreCase(record.status)) {
            JOptionPane.showMessageDialog(this, "Selected transfer is already completed.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select local file to resume sending: " + record.fileName);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File localFile = chooser.getSelectedFile();

            new Thread(() -> {
                try {
                    InetAddress peerAddress = InetAddress.getByName(record.peerIp);
                    boolean success = FileTransferClient.sendFile(peerAddress, targetPort, localFile, (sent, total) -> {});
                    
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, success ? "Resume completed successfully!" : "Resume failed.", "Transfer Status", success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                        loadHistory();
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error during resume: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            }, "ResumeTransferThread").start();
        }
    }
}