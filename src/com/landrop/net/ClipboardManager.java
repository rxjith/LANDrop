package com.landrop.net;

import com.landrop.util.AppConfig;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.function.Consumer;

public class ClipboardManager {

    private String lastCopiedText = "";
    private String lastCopiedImageHash = "";
    private Thread monitorThread;
    private volatile boolean running = false;

    public void startMonitoring(Consumer<String> onClipboardPayloadReady) {
        running = true;
        monitorThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000); // Poll system clipboard
                    if (!AppConfig.isClipboardSyncEnabled()) continue;

                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

                    // 1. Check for text flavor
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String currentText = (String) clipboard.getData(DataFlavor.stringFlavor);
                        if (currentText != null && !currentText.equals(lastCopiedText) && !currentText.trim().isEmpty()) {
                            lastCopiedText = currentText;
                            lastCopiedImageHash = ""; // reset image tracking
                            onClipboardPayloadReady.accept("[CLIPBOARD]: " + currentText);
                            continue;
                        }
                    }

                    // 2. Check for image flavor
                    if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                        Image img = (Image) clipboard.getData(DataFlavor.imageFlavor);
                        if (img != null) {
                            String base64Img = imageToBase64(img);
                            if (base64Img != null && !base64Img.equals(lastCopiedImageHash)) {
                                lastCopiedImageHash = base64Img;
                                lastCopiedText = ""; // reset text tracking
                                onClipboardPayloadReady.accept("[IMAGE_CLIPBOARD]: " + base64Img);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }, "ClipboardMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public void applyRemotePayload(String payload) {
        if (!AppConfig.isClipboardSyncEnabled() || payload == null) return;
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (payload.startsWith("[CLIPBOARD]: ")) {
                String text = payload.substring(13);
                this.lastCopiedText = text;
                this.lastCopiedImageHash = "";
                StringSelection selection = new StringSelection(text);
                clipboard.setContents(selection, selection);
            } else if (payload.startsWith("[IMAGE_CLIPBOARD]: ")) {
                String base64Img = payload.substring(19);
                this.lastCopiedImageHash = base64Img;
                this.lastCopiedText = "";
                Image img = base64ToImage(base64Img);
                if (img != null) {
                    TransferableImage transferable = new TransferableImage(img);
                    clipboard.setContents(transferable, null);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String imageToBase64(Image img) {
        try {
            BufferedImage bImage = toBufferedImage(img);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(bImage, "png", bos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private Image base64ToImage(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            return ImageIO.read(bis);
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }
        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();
        return bimage;
    }

    // Helper Transferable class to set images into system clipboard
    private static class TransferableImage implements Transferable {
        private final Image image;

        public TransferableImage(Image image) {
            this.image = image;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.imageFlavor };
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(DataFlavor.imageFlavor);
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (flavor.equals(DataFlavor.imageFlavor)) {
                return image;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }

    public void stop() {
        running = false;
        if (monitorThread != null) monitorThread.interrupt();
    }
}