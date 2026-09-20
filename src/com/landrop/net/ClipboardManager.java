package com.landrop.net;

import com.landrop.util.AppConfig;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.util.function.Consumer;

public class ClipboardManager {

    private String lastCopiedText = "";
    private Thread monitorThread;
    private volatile boolean running = false;

    public void startMonitoring(Consumer<String> onLocalClipboardChanged) {
        running = true;
        monitorThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000); // Poll system clipboard
                    if (!AppConfig.isClipboardSyncEnabled()) continue;

                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String currentText = (String) clipboard.getData(DataFlavor.stringFlavor);
                        if (currentText != null && !currentText.equals(lastCopiedText) && !currentText.trim().isEmpty()) {
                            lastCopiedText = currentText;
                            onLocalClipboardChanged.accept(currentText);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }, "ClipboardMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public void applyRemoteTextToClipboard(String text) {
        if (!AppConfig.isClipboardSyncEnabled() || text == null || text.trim().isEmpty()) return;
        try {
            this.lastCopiedText = text;
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(text);
            clipboard.setContents(selection, selection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        if (monitorThread != null) monitorThread.interrupt();
    }
}