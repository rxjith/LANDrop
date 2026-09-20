package com.landrop.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

public class TrayManager {

    private static SystemTray systemTray;
    private static TrayIcon trayIcon;

    public TrayManager() {
        // Default constructor
    }

    public TrayManager(JFrame mainFrame) {
        initializeTray(mainFrame);
    }

    public static synchronized void initializeTray(JFrame mainFrame) {
        if (!SystemTray.isSupported()) {
            System.out.println("[TrayManager] SystemTray is not supported on this system.");
            return;
        }

        if (trayIcon != null) {
            return; // Already initialized
        }

        Image image = null;
        URL iconUrl = TrayManager.class.getResource("/icon.png");

        if (iconUrl != null) {
            image = new ImageIcon(iconUrl).getImage();
        } else if (mainFrame != null && mainFrame.getIconImage() != null) {
            image = mainFrame.getIconImage();
        } else {
            image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }

        PopupMenu popup = new PopupMenu();

        MenuItem openItem = new MenuItem("Open LANDrop");
        openItem.addActionListener(e -> {
            if (mainFrame != null) {
                mainFrame.setVisible(true);
                mainFrame.setState(Frame.NORMAL);
                mainFrame.toFront();
            }
        });

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        popup.add(openItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(image, "LANDrop", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> {
            if (mainFrame != null) {
                mainFrame.setVisible(true);
                mainFrame.setState(Frame.NORMAL);
                mainFrame.toFront();
            }
        });

        systemTray = SystemTray.getSystemTray();
        try {
            systemTray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("[TrayManager] Could not add TrayIcon: " + e.getMessage());
        }
    }

    public static void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, type);
        }
    }

    public static void displayNotification(String title, String message, TrayIcon.MessageType type) {
        showNotification(title, message, type);
    }
}