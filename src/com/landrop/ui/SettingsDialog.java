package com.landrop.ui;

import com.landrop.util.AppConfig;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

public class SettingsDialog extends JDialog {

    private final JTextField dirField;
    private final JCheckBox autoAcceptCheckBox;
    private final JCheckBox clipboardSyncCheckBox;
    private final JTextField portField;
    private final JTextField speedLimitField;

    public SettingsDialog(Frame parent) {
        super(parent, "LANDrop Settings", true);
        setSize(580, 320);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Download Directory
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Download Directory:"), gbc);

        dirField = new JTextField(AppConfig.getDownloadDir(), 20);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(dirField, gbc);

        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(dirField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                dirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        gbc.gridx = 2; gbc.weightx = 0.0;
        formPanel.add(browseBtn, gbc);

        // Row 1: Auto-accept trusted peers
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        autoAcceptCheckBox = new JCheckBox("Auto-accept incoming transfers from trusted peers", AppConfig.isAutoAcceptTrusted());
        formPanel.add(autoAcceptCheckBox, gbc);

        // Row 2: Cross-Device Clipboard Sync
        clipboardSyncCheckBox = new JCheckBox("Cross-Device Clipboard Sync (Auto-share copied text across subnet)", AppConfig.isClipboardSyncEnabled());
        gbc.gridy = 2;
        formPanel.add(clipboardSyncCheckBox, gbc);

        // Row 3: TCP Port
        gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0.0;
        formPanel.add(new JLabel("File Transfer Port (TCP):"), gbc);

        portField = new JTextField(String.valueOf(AppConfig.getTcpPort()), 8);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(portField, gbc);

        // Row 4: Bandwidth Throttling Speed Limit
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Max Bandwidth (KB/s, 0=Unlimited):"), gbc);

        speedLimitField = new JTextField(String.valueOf(AppConfig.getBandwidthLimitKbps()), 8);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(speedLimitField, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Bottom Action Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save & Apply");
        JButton cancelBtn = new JButton("Cancel");

        saveBtn.addActionListener(e -> {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                int speedLimit = Integer.parseInt(speedLimitField.getText().trim());

                if (port < 1024 || port > 65535) {
                    JOptionPane.showMessageDialog(this, "Port must be between 1024 and 65535.", "Invalid Port", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String pathText = dirField.getText().trim();
                try {
                    Paths.get(pathText); // Validate path syntax
                } catch (InvalidPathException ex) {
                    JOptionPane.showMessageDialog(this, "Specified directory path contains invalid characters.", "Invalid Path", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                File dir = new File(pathText);
                if (!dir.exists() && !dir.mkdirs()) {
                    JOptionPane.showMessageDialog(this, "Could not create specified download directory. Check permissions.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                boolean portChanged = (port != AppConfig.getTcpPort());

                AppConfig.setDownloadDir(dir.getAbsolutePath());
                AppConfig.setAutoAcceptTrusted(autoAcceptCheckBox.isSelected());
                AppConfig.setClipboardSyncEnabled(clipboardSyncCheckBox.isSelected());
                AppConfig.setTcpPort(port);
                AppConfig.setBandwidthLimitKbps(Math.max(0, speedLimit));

                if (portChanged) {
                    JOptionPane.showMessageDialog(this, "Settings saved successfully!\nNote: Restart LANDrop for the new TCP port binding to take effect.", "Settings Saved", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Settings saved successfully!", "Settings Saved", JOptionPane.INFORMATION_MESSAGE);
                }
                
                dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Port and Speed Limit must be valid integers.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelBtn.addActionListener(e -> dispose());

        bottomPanel.add(saveBtn);
        bottomPanel.add(cancelBtn);
        add(bottomPanel, BorderLayout.SOUTH);
    }
}
