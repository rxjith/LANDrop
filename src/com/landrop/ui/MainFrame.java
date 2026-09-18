package com.landrop.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.landrop.db.DatabaseManager;
import com.landrop.model.PeerDevice;
import com.landrop.net.FileTransferClient;
import com.landrop.net.FileTransferServer;
import com.landrop.net.MulticastDiscoveryService;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MainFrame extends JFrame {

    private final DefaultListModel<PeerDevice> peerListModel = new DefaultListModel<>();
    private final JList<PeerDevice> peerList = new JList<>(peerListModel);
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Status: Idle");

    private MulticastDiscoveryService discoveryService;
    private FileTransferServer fileServer;
    private final int localTcpPort = 52145;

    public MainFrame(String deviceName) {
        setTitle("LANDrop - " + deviceName);
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initBackend(deviceName);
        initUI();
        startPeerRefreshTimer();
    }

    private void initBackend(String deviceName) {
        DatabaseManager.initializeDatabase();

        Path downloadDir = Paths.get(System.getProperty("user.home"), "Downloads", "LANDrop");
        fileServer = new FileTransferServer(localTcpPort, downloadDir.toString());
        try {
            fileServer.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to start File Server: " + e.getMessage(), "Server Error", JOptionPane.ERROR_MESSAGE);
        }

        discoveryService = new MulticastDiscoveryService();
        discoveryService.start(deviceName, localTcpPort);
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        headerPanel.add(new JLabel("<html><h2>LANDrop File Sharing Engine</h2></html>"));
        add(headerPanel, BorderLayout.NORTH);

        // Discovered Peers List
        peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peerList.setBorder(BorderFactory.createTitledBorder("Active Peers"));
        JScrollPane scrollPane = new JScrollPane(peerList);

        // Drag and Drop Area
        JPanel dropZone = new JPanel(new GridBagLayout());
        dropZone.setBorder(BorderFactory.createTitledBorder("Drop Zone"));
        JLabel dropLabel = new JLabel("<html><center>Drag & Drop Files Here<br><small>(Select a peer first)</small></center></html>");
        dropZone.add(dropLabel);

        setupDragAndDrop(dropZone);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, dropZone);
        splitPane.setDividerLocation(320);
        add(splitPane, BorderLayout.CENTER);

        // Status Footer
        JPanel statusPanel = new JPanel(new BorderLayout(10, 10));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        progressBar.setStringPainted(true);

        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void setupDragAndDrop(JPanel dropZone) {
        new DropTarget(dropZone, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    PeerDevice selectedPeer = peerList.getSelectedValue();
                    if (selectedPeer == null) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Please select an active peer from the list first.", "No Target Selected", JOptionPane.WARNING_MESSAGE);
                        dtde.rejectDrop();
                        return;
                    }

                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                    if (!droppedFiles.isEmpty()) {
                        sendFileToPeer(selectedPeer, droppedFiles.get(0));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void sendFileToPeer(PeerDevice peer, File file) {
        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Sending " + file.getName() + "..."));
                
                boolean success = FileTransferClient.sendFile(peer.getIpAddress(), peer.getPort() > 0 ? peer.getPort() : localTcpPort, file, (sent, total) -> {
                    int pct = (int) ((sent * 100) / total);
                    SwingUtilities.invokeLater(() -> progressBar.setValue(pct));
                });

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(success ? "Transfer complete: " + file.getName() : "Transfer failed.");
                    progressBar.setValue(success ? 100 : 0);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    progressBar.setValue(0);
                });
            }
        }, "GUI-FileSender").start();
    }

    private void startPeerRefreshTimer() {
        Timer timer = new Timer(2000, e -> {
            PeerDevice currentSelection = peerList.getSelectedValue();
            peerListModel.clear();
            discoveryService.getActivePeers().values().forEach(peerListModel::addElement);
            if (currentSelection != null) {
                peerList.setSelectedValue(currentSelection, true);
            }
        });
        timer.start();
    }

    @Override
    public void dispose() {
        if (fileServer != null) fileServer.stop();
        if (discoveryService != null) discoveryService.stop();
        super.dispose();
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame("ThinkPad-T470");
            frame.setVisible(true);
        });
    }
}