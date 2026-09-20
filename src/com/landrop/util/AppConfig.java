package com.landrop.util;

import java.io.File;
import java.util.prefs.Preferences;

public class AppConfig {

    private static final Preferences prefs = Preferences.userNodeForPackage(AppConfig.class);

    private static final String KEY_DOWNLOAD_DIR = "download_dir";
    private static final String KEY_AUTO_ACCEPT_TRUSTED = "auto_accept_trusted";
    private static final String KEY_TCP_PORT = "tcp_port";
    private static final String KEY_STEALTH_MODE = "stealth_mode";
    private static final String KEY_BANDWIDTH_LIMIT_KBPS = "bandwidth_limit_kbps";
    private static final String KEY_CLIPBOARD_SYNC = "clipboard_sync";

    public static String getDownloadDir() {
        String defaultDir = new File(System.getProperty("user.home"), "Downloads/LANDrop").getAbsolutePath();
        return prefs.get(KEY_DOWNLOAD_DIR, defaultDir);
    }

    public static void setDownloadDir(String dir) {
        prefs.put(KEY_DOWNLOAD_DIR, dir);
    }

    public static boolean isAutoAcceptTrusted() {
        return prefs.getBoolean(KEY_AUTO_ACCEPT_TRUSTED, false);
    }

    public static void setAutoAcceptTrusted(boolean autoAccept) {
        prefs.putBoolean(KEY_AUTO_ACCEPT_TRUSTED, autoAccept);
    }

    public static int getTcpPort() {
        return prefs.getInt(KEY_TCP_PORT, 52145);
    }

    public static void setTcpPort(int port) {
        prefs.putInt(KEY_TCP_PORT, port);
    }

    public static boolean isStealthMode() {
        return prefs.getBoolean(KEY_STEALTH_MODE, false);
    }

    public static void setStealthMode(boolean stealth) {
        prefs.putBoolean(KEY_STEALTH_MODE, stealth);
    }

    public static int getBandwidthLimitKbps() {
        return prefs.getInt(KEY_BANDWIDTH_LIMIT_KBPS, 0); // 0 = Unlimited
    }

    public static void setBandwidthLimitKbps(int kbps) {
        prefs.putInt(KEY_BANDWIDTH_LIMIT_KBPS, kbps);
    }

    public static boolean isClipboardSyncEnabled() {
        return prefs.getBoolean(KEY_CLIPBOARD_SYNC, true);
    }

    public static void setClipboardSyncEnabled(boolean enabled) {
        prefs.putBoolean(KEY_CLIPBOARD_SYNC, enabled);
    }
}