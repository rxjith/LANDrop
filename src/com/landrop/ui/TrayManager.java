package com.landrop.ui;

import javax.swing.*;
import java.awt.*;

public class TrayManager {

    private static TrayIcon trayIcon;

    public static void initializeTray(JFrame mainFrame) {
        if (!SystemTray.isSupported()) {
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();

        // Generate simple default system tray icon
        Image iconImage = new ImageIcon(mainFrame.getClass().getResource("/icon.png") != null ?
                mainFrame.getClass().getResource("/icon.png") :
                Toolkit.getDefaultToolkit().getImage("")).getImage();

        PopupMenu popup = new PopupMenu();
        MenuItem showItem = new MenuItem("Open LANDrop");
        MenuItem exitItem = new MenuItem("Exit");

        showItem.addActionListener(e -> {
            mainFrame.setVisible(true);
            mainFrame.setState(Frame.NORMAL);
        });

        exitItem.addActionListener(e -> {
            mainFrame.dispose();
            System.exit(0);
        });

        popup.add(showItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(iconImage, "LANDrop Local Mesh", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> {
            mainFrame.setVisible(true);
            mainFrame.setState(Frame.NORMAL);
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public static void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, type);
        }
    }
}