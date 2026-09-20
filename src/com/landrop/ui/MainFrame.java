package com.landrop.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.landrop.db.DatabaseManager;
import com.landrop.model.PeerDevice;
import com.landrop.net.ChatManager;
import com.landrop.net.FileTransferClient;
import com.landrop.net.FileTransferServer;
import com.landrop.net.MulticastDiscoveryService;


import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MainFrame extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rootPanel = new JPanel(cardLayout);

    // Backend Services
    private MulticastDiscoveryService discoveryService;
    private FileTransferServer fileServer;
    private final int localTcpPort = 52145;
    private String localDeviceName;
    private ChatManager chatManager;

    // Discovery UI Components
    private final DefaultListModel<PeerDevice> peerListModel = new DefaultListModel<>();
    private final JList<PeerDevice> peerList = new JList<>(peerListModel);
    private RadarPanel radarPanel;

    // Transfer Room Components
    private PeerDevice targetPeer;
    private JLabel transferHeaderLabel;
    private JTextArea chatLogArea;
    private JTextField chatInputField;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    public MainFrame() {
        setTitle("LANDrop Local Mesh");
        setSize(950, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initViews();
        add(rootPanel);
    }

    private void initViews() {
        rootPanel.add(buildWelcomeCard(), "WELCOME");
        rootPanel.add(buildDiscoveryCard(), "DISCOVERY");
        rootPanel.add(buildTransferCard(), "TRANSFER");
        cardLayout.show(rootPanel, "WELCOME");
    }

    //CARD 1: WELCOME & ONBOARDING
    private JPanel buildWelcomeCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(24, 26, 31));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridx = 0;

        JLabel title = new JLabel("Welcome to LANDrop");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(new Color(230, 235, 240));

        JLabel subtitle = new JLabel("Zero-configuration high-speed peer file transfer");
        subtitle.setForeground(Color.GRAY);

        String defaultName;
        try {
            defaultName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            defaultName = "Node-" + (System.currentTimeMillis() % 1000);
        }

        JTextField nameField = new JTextField(defaultName, 18);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        JButton startBtn = new JButton("Join Network");
        startBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));

        startBtn.addActionListener(e -> {
            String chosenName = nameField.getText().trim();
            if (!chosenName.isEmpty()) {
                this.localDeviceName = chosenName;
                setTitle("LANDrop - " + chosenName);
                initBackend(chosenName);
                startPeerRefreshTimer();
                cardLayout.show(rootPanel, "DISCOVERY");
            }
        });

        gbc.gridy = 0; panel.add(title, gbc);
        gbc.gridy = 1; panel.add(subtitle, gbc);
        gbc.gridy = 2; panel.add(new JLabel("Device Name on Local Mesh:"), gbc);
        gbc.gridy = 3; panel.add(nameField, gbc);
        gbc.gridy = 4; panel.add(startBtn, gbc);

        return panel;
    }

    //CARD 2: RADAR & PEER DISCOVERY
    private JPanel buildDiscoveryCard() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        radarPanel = new RadarPanel();

        // Active peer selector sidebar
        JPanel sidebar = new JPanel(new BorderLayout(5, 5));
        sidebar.setPreferredSize(new Dimension(280, 0));
        sidebar.setBorder(BorderFactory.createTitledBorder("Active Peers in Range"));

        peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sidebar.add(new JScrollPane(peerList), BorderLayout.CENTER);

        JButton connectBtn = new JButton("Open Direct Transfer Room");
        connectBtn.addActionListener(e -> {
            PeerDevice selected = peerList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a discovered peer from the list first.", "No Target", JOptionPane.WARNING_MESSAGE);
                return;
            }
            enterTransferRoom(selected);
        });
        sidebar.add(connectBtn, BorderLayout.SOUTH);

        panel.add(radarPanel, BorderLayout.CENTER);
        panel.add(sidebar, BorderLayout.EAST);
        return panel;
    }

    //CARD 3: CHAT & DIRECT FILE TRANSFER ROOM 
    private JPanel buildTransferCard() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top Header
        JPanel topBar = new JPanel(new BorderLayout());
        JButton backBtn = new JButton("← Back to Radar");
        backBtn.addActionListener(e -> cardLayout.show(rootPanel, "DISCOVERY"));

        transferHeaderLabel = new JLabel("Direct Session: Not Connected");
        transferHeaderLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));

        topBar.add(backBtn, BorderLayout.WEST);
        topBar.add(transferHeaderLabel, BorderLayout.CENTER);
        panel.add(topBar, BorderLayout.NORTH);

        // Center split: Chat Console on Left, Drop Zone on Right
        chatLogArea = new JTextArea();
        chatLogArea.setEditable(false);
        chatLogArea.setLineWrap(true);
        JScrollPane chatScroll = new JScrollPane(chatLogArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("Session Messaging"));

        JPanel chatInputPanel = new JPanel(new BorderLayout(5, 5));
        chatInputField = new JTextField();
        JButton sendMsgBtn = new JButton("Send");
        chatInputPanel.add(chatInputField, BorderLayout.CENTER);
        chatInputPanel.add(sendMsgBtn, BorderLayout.EAST);

        Runnable sendAction = () -> {
            String text = chatInputField.getText().trim();
            if (!text.isEmpty()) {
                chatLogArea.append("[" + localDeviceName + "]: " + text + "\n");
                chatInputField.setText("");
                
                // Dispatch over the network to the active peer
                if (targetPeer != null && targetPeer.getIpAddress() != null) {
                    ChatManager.sendMessageAsync(
                        targetPeer.getIpAddress().getHostAddress(), 
                        localDeviceName, 
                        text
                    );
                }
            }
        };
        sendMsgBtn.addActionListener(e -> sendAction.run());
        chatInputField.addActionListener(e -> sendAction.run());

        JPanel chatContainer = new JPanel(new BorderLayout(5, 5));
        chatContainer.add(chatScroll, BorderLayout.CENTER);
        chatContainer.add(chatInputPanel, BorderLayout.SOUTH);

        // File drop zone
        JPanel dropZone = new JPanel(new GridBagLayout());
        dropZone.setBorder(BorderFactory.createTitledBorder("Direct Drop Zone"));
        JLabel dropLabel = new JLabel("<html><center><h3>Drag & Drop Files Here</h3><p>Direct Stream to Target Peer</p></center></html>");
        dropZone.add(dropLabel);
        setupDragAndDrop(dropZone);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatContainer, dropZone);
        splitPane.setDividerLocation(420);
        panel.add(splitPane, BorderLayout.CENTER);

        // Status & Progress Footer
        JPanel footer = new JPanel(new BorderLayout(10, 5));
        statusLabel = new JLabel("Status: Idle");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(progressBar, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    private void enterTransferRoom(PeerDevice peer) {
        this.targetPeer = peer;
        transferHeaderLabel.setText("   Connected: " + peer.getHostname() + " (" + peer.getIpAddress() + ")");
        chatLogArea.setText("--- Session established with " + peer.getHostname() + " ---\n");
        statusLabel.setText("Status: Ready to send files.");
        progressBar.setValue(0);
        cardLayout.show(rootPanel, "TRANSFER");
    }

    private void initBackend(String deviceName) {
        DatabaseManager.initializeDatabase();
        Path downloadDir = Paths.get(System.getProperty("user.home"), "Downloads", "LANDrop");
        fileServer = new FileTransferServer(localTcpPort, downloadDir.toString());

        // Prompt receiver before saving files from untrusted peers
        fileServer.setAcceptanceListener((peerIp, fileName, fileSize) -> {
            try {
                final boolean[] accepted = new boolean[1];
                SwingUtilities.invokeAndWait(() -> {
                    String sizeStr = String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
                    
                    JCheckBox trustCheckbox = new JCheckBox("Always trust this peer (" + peerIp + ")");
                    Object[] message = {
                        "Incoming file transfer from " + peerIp + ":",
                        "File: " + fileName,
                        "Size: " + sizeStr,
                        "\nDo you want to accept this file?",
                        trustCheckbox
                    };

                    int choice = JOptionPane.showConfirmDialog(
                        MainFrame.this,
                        message,
                        "Incoming Transfer Request",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                    );

                    if (choice == JOptionPane.YES_OPTION) {
                        accepted[0] = true;
                        if (trustCheckbox.isSelected()) {
                            DatabaseManager.setPeerTrust(peerIp, peerIp, true);
                        }
                    } else {
                        accepted[0] = false;
                    }
                });
                return accepted[0];
            } catch (Exception e) {
                return false;
            }
        });

        try {
            fileServer.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to start File Server: " + e.getMessage(), "Server Error", JOptionPane.ERROR_MESSAGE);
        }

        discoveryService = new MulticastDiscoveryService();
        discoveryService.start(deviceName, localTcpPort);

        chatManager = new ChatManager(incomingMsg -> {
            SwingUtilities.invokeLater(() -> {
                if (chatLogArea != null) {
                    chatLogArea.append(incomingMsg + "\n");
                }
            });
        });
        chatManager.startServer();
    }

    private void setupDragAndDrop(JPanel dropZone) {
        new DropTarget(dropZone, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (targetPeer == null) {
                        JOptionPane.showMessageDialog(MainFrame.this, "No active peer session.", "Error", JOptionPane.ERROR_MESSAGE);
                        dtde.rejectDrop();
                        return;
                    }

                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                    if (!droppedFiles.isEmpty()) {
                        sendBatchToPeer(targetPeer, droppedFiles);
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
                SwingUtilities.invokeLater(() -> statusLabel.setText("Streaming " + file.getName() + "..."));

                boolean success = FileTransferClient.sendFile(peer.getIpAddress(), peer.getPort() > 0 ? peer.getPort() : localTcpPort, file, (sent, total) -> {
                    int pct = (int) ((sent * 100) / total);
                    SwingUtilities.invokeLater(() -> progressBar.setValue(pct));
                });

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(success ? "Transfer complete: " + file.getName() : "Transfer failed.");
                    progressBar.setValue(success ? 100 : 0);
                    if (success) {
                        chatLogArea.append("[System]: Successfully sent file '" + file.getName() + "'\n");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    progressBar.setValue(0);
                });
            }
        }, "FileSenderThread").start();
    }

    private void sendBatchToPeer(PeerDevice peer, List<File> files) {
        FileTransferClient.sendBatch(
            peer.getIpAddress(),
            peer.getPort() > 0 ? peer.getPort() : localTcpPort,
            files,
            new FileTransferClient.BatchProgressCallback() {
                @Override
                public void onFileStart(String fileName, int currentIndex, int totalFiles) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(String.format("Sending [%d/%d]: %s", currentIndex, totalFiles, fileName));
                        progressBar.setValue(0);
                    });
                }

                @Override
                public void onFileProgress(long bytesSent, long totalBytes) {
                    int pct = (int) ((bytesSent * 100) / totalBytes);
                    SwingUtilities.invokeLater(() -> progressBar.setValue(pct));
                }

                @Override
                public void onBatchComplete(int successCount, int failedCount) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(String.format("Batch complete! Sent: %d, Failed: %d", successCount, failedCount));
                        progressBar.setValue(failedCount == 0 ? 100 : 0);
                        chatLogArea.append(String.format("[System]: Batch transfer finished (%d succeeded, %d failed)\n", successCount, failedCount));
                    });
                }
            }
        );
    }


    private void startPeerRefreshTimer() {
        Timer timer = new Timer(2000, e -> {
            PeerDevice currentSelection = peerList.getSelectedValue();
            peerListModel.clear();
            
            // Filter self out
            List<PeerDevice> active = discoveryService.getActivePeers().values().stream()
                    .filter(p -> !p.getHostname().equalsIgnoreCase(localDeviceName))
                    .toList();

            active.forEach(peerListModel::addElement);
            if (radarPanel != null) {
                radarPanel.updatePeers(active);
            }

            if (currentSelection != null) {
                peerList.setSelectedValue(currentSelection, true);
            }
        });
        timer.start();
    }

    @Override
    public void dispose() {
        if (chatManager != null) chatManager.stop();
        if (fileServer != null) fileServer.stop();
        if (discoveryService != null) discoveryService.stop();
        super.dispose();
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}