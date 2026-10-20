package com.landrop.ui;

import com.landrop.model.PeerDevice;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.function.Consumer;

public class RadarPanel extends JPanel {

    private static final Color BG = new Color(18, 22, 28);
    private static final Color RING_LIVE = new Color(35, 75, 55);
    private static final Color RING_GHOST = new Color(62, 72, 94);
    private static final Color ACCENT_LIVE = new Color(50, 220, 120);
    private static final Color ACCENT_GHOST = new Color(150, 170, 215);

    private static final int BTN_W = 188;
    private static final int BTN_H = 34;
    private static final int BTN_MARGIN = 8; // was 16

    private Collection<PeerDevice> peers = Collections.emptyList();
    private double sweepAngle = 0.0;
    private final Timer sweepTimer;

    // Ghost mode state. 'ghost' animates 0 (visible) -> 1 (fully ghosted).
    private boolean ghostMode = false;
    private double ghost = 0.0;
    private boolean hoverButton = false;
    private Consumer<Boolean> ghostToggleListener;
    private Rectangle2D buttonBounds = new Rectangle2D.Double();

    private final long startNanos = System.nanoTime();
    private int frame = 0;

    public RadarPanel() {
        setBackground(BG);

        sweepTimer = new Timer(16, e -> {
            double target = ghostMode ? 1.0 : 0.0;
            if (ghost < target) ghost = Math.min(target, ghost + 0.03);
            else if (ghost > target) ghost = Math.max(target, ghost - 0.03);

            // The sweep spins down as ghost mode engages and spins back up when it ends
            sweepAngle = (sweepAngle + 0.04 * (1.0 - ease(ghost))) % (2 * Math.PI);
            frame++;
            repaint();
        });
        sweepTimer.start();

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean over = buttonBounds.contains(e.getPoint());
                if (over != hoverButton) {
                    hoverButton = over;
                    setCursor(Cursor.getPredefinedCursor(over ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverButton) {
                    hoverButton = false;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && buttonBounds.contains(e.getPoint())) {
                    setGhostMode(!ghostMode, true);
                    if (ghostToggleListener != null) ghostToggleListener.accept(ghostMode);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void updatePeers(Collection<PeerDevice> activePeers) {
        this.peers = activePeers;
        repaint();
    }

    /** Called with the new state whenever the user clicks the ghost button. */
    public void setGhostToggleListener(Consumer<Boolean> listener) {
        this.ghostToggleListener = listener;
    }

    /** Sets ghost mode without firing the listener. Pass animate=false to snap (e.g. on startup). */
    public void setGhostMode(boolean enabled, boolean animate) {
        this.ghostMode = enabled;
        if (!animate) this.ghost = enabled ? 1.0 : 0.0;
        repaint();
    }

    public boolean isGhostMode() {
        return ghostMode;
    }

    private static double ease(double x) {
        return x * x * (3 - 2 * x);
    }

    private static Color mix(Color a, Color b, double t, int alpha) {
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl, Math.max(0, Math.min(255, alpha)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        int maxRadius = Math.min(centerX, centerY) - 20;
        double t = ease(ghost);
        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;

        buttonBounds = new Rectangle2D.Double(width - BTN_W - BTN_MARGIN, BTN_MARGIN, BTN_W, BTN_H);

        if (maxRadius <= 10) {       // Panel too small to draw the radar, but keep the button usable
            drawGhostButton(g2d, t);
            g2d.dispose();
            return;
        }

        // Live rings fade out, dashed ghost rings fade in
        g2d.setStroke(new BasicStroke(1.2f));
        g2d.setColor(mix(RING_LIVE, RING_LIVE, 0, (int) (255 * (1 - t))));
        for (int r = maxRadius / 4; r <= maxRadius; r += maxRadius / 4) {
            g2d.drawOval(centerX - r, centerY - r, 2 * r, 2 * r);
        }
        if (t > 0.01) {
            g2d.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 8f}, 0f));
            g2d.setColor(mix(RING_GHOST, RING_GHOST, 0, (int) (255 * t)));
            for (int r = maxRadius / 4; r <= maxRadius; r += maxRadius / 4) {
                g2d.drawOval(centerX - r, centerY - r, 2 * r, 2 * r);
            }
        }

        g2d.setStroke(new BasicStroke(1.2f));
        g2d.setColor(mix(RING_LIVE, RING_GHOST, t, 255));
        g2d.drawLine(centerX - maxRadius, centerY, centerX + maxRadius, centerY);
        g2d.drawLine(centerX, centerY - maxRadius, centerX, centerY + maxRadius);

        // Sweep line (fades with ghost progress)
        if (t < 0.99) {
            int sweepX = centerX + (int) (maxRadius * Math.cos(sweepAngle));
            int sweepY = centerY + (int) (maxRadius * Math.sin(sweepAngle));
            double live = 1.0 - t;
            g2d.setPaint(new GradientPaint(centerX, centerY, new Color(50, 220, 120, (int) (180 * live)),
                    sweepX, sweepY, new Color(20, 160, 80, (int) (20 * live))));
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawLine(centerX, centerY, sweepX, sweepY);
        }

        // Peer blips fade away as the radar goes dark
        double peerFade = 1.0 - t;
        if (peerFade > 0.02) {
            for (PeerDevice peer : peers) {
                Point2D pt = calculatePeerPosition(peer, centerX, centerY, maxRadius);
                int px = (int) pt.getX();
                int py = (int) pt.getY();

                g2d.setColor(new Color(0, 255, 150, (int) (70 * peerFade)));
                g2d.fillOval(px - 9, py - 9, 18, 18);

                g2d.setColor(new Color(0, 255, 170, (int) (255 * peerFade)));
                g2d.fillOval(px - 4, py - 4, 8, 8);

                g2d.setColor(new Color(210, 230, 220, (int) (255 * peerFade)));
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2d.drawString(peer.getHostname(), px + 8, py - 2);
            }
        }

        if (t > 0.01) {
            drawGhostOverlay(g2d, t, seconds, width, height, centerX, centerY, maxRadius);
        }

        drawGhostButton(g2d, t);
        g2d.dispose();
    }

    private void drawGhostOverlay(Graphics2D g2d, double t, double seconds, int width, int height,
                                  int cx, int cy, int maxRadius) {
        // Dark vignette to make the whole radar feel powered down
        g2d.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), Math.max(width, height) * 0.7f,
                new float[]{0.35f, 1f},
                new Color[]{new Color(10, 12, 20, 0), new Color(10, 12, 20, (int) (170 * t))}));
        g2d.fillRect(0, 0, width, height);

        // Faint scanlines, like a dead CRT
        g2d.setColor(new Color(0, 0, 0, (int) (40 * t)));
        for (int y = 0; y < height; y += 3) {
            g2d.drawLine(0, y, width, y);
        }

        // Breathing halo behind the ghost
        double breathe = 0.5 + 0.5 * Math.sin(seconds * 1.4);
        float haloR = (float) (maxRadius * (0.38 + 0.05 * breathe));
        g2d.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), haloR,
                new float[]{0f, 1f},
                new Color[]{new Color(150, 170, 215, (int) ((55 + 25 * breathe) * t)), new Color(150, 170, 215, 0)}));
        g2d.fillOval((int) (cx - haloR), (int) (cy - haloR), (int) (haloR * 2), (int) (haloR * 2));

        // Floating ghost
        double size = Math.max(36, maxRadius * 0.26);
        double bob = Math.sin(seconds * 1.6) * 5 * t;
        drawGhost(g2d, cx, cy - size * 0.25 + bob, size, mix(ACCENT_GHOST, ACCENT_GHOST, 0, (int) (235 * t)),
                new Color(18, 22, 28, (int) (255 * t)));

        // Labels
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 15));
        drawSpacedCentered(g2d, "GHOST MODE", cx, (int) (cy + size * 0.85), 4,
                mix(ACCENT_GHOST, ACCENT_GHOST, 0, (int) (240 * t)));
        g2d.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        drawSpacedCentered(g2d, "Hidden from the network \u00B7 Discovery beacons paused", cx, (int) (cy + size * 0.85 + 22), 0,
                new Color(140, 150, 175, (int) (200 * t)));

        // Occasional glitch bars once fully ghosted
        if (t > 0.9 && (frame / 2) % 150 < 3) {
            Random rnd = new Random(frame / 2);
            g2d.setColor(new Color(150, 170, 215, 28));
            for (int i = 0; i < 2; i++) {
                g2d.fillRect(0, rnd.nextInt(Math.max(1, height - 6)), width, 2 + rnd.nextInt(4));
            }
        }
    }

    private void drawSpacedCentered(Graphics2D g2d, String text, int cx, int baseline, int spacing, Color color) {
        FontMetrics fm = g2d.getFontMetrics();
        int total = 0;
        for (char c : text.toCharArray()) total += fm.charWidth(c) + spacing;
        total -= spacing;
        int x = cx - total / 2;
        g2d.setColor(color);
        for (char c : text.toCharArray()) {
            g2d.drawString(String.valueOf(c), x, baseline);
            x += fm.charWidth(c) + spacing;
        }
    }

    private void drawGhostButton(Graphics2D g2d, double t) {
        Rectangle2D b = buttonBounds;
        RoundRectangle2D pill = new RoundRectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                b.getHeight(), b.getHeight());

        if (hoverButton) {
            g2d.setColor(mix(ACCENT_LIVE, ACCENT_GHOST, t, 45));
            g2d.setStroke(new BasicStroke(6f));
            g2d.draw(pill);
        }
        g2d.setColor(mix(new Color(20, 45, 34), new Color(36, 42, 66), t, hoverButton ? 235 : 200));
        g2d.fill(pill);
        g2d.setColor(mix(ACCENT_LIVE, ACCENT_GHOST, t, hoverButton ? 255 : 160));
        g2d.setStroke(new BasicStroke(1.4f));
        g2d.draw(pill);

        // Mini ghost icon
        drawGhost(g2d, b.getX() + 22, b.getCenterY(), 15, mix(ACCENT_LIVE, ACCENT_GHOST, t, 255),
                mix(new Color(20, 45, 34), new Color(36, 42, 66), t, 255));

        // Label
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2d.setColor(mix(new Color(200, 235, 215), new Color(215, 222, 245), t, 255));
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString("Ghost Mode", (int) (b.getX() + 40), (int) (b.getCenterY() + fm.getAscent() / 2.0 - 1));

        // Switch
        double trackW = 30, trackH = 16;
        double trackX = b.getMaxX() - 18 - trackW;
        double trackY = b.getCenterY() - trackH / 2;
        g2d.setColor(mix(new Color(45, 60, 55), ACCENT_GHOST, t, 255));
        g2d.fill(new RoundRectangle2D.Double(trackX, trackY, trackW, trackH, trackH, trackH));
        double knobX = trackX + 2 + (trackW - trackH) * t;
        g2d.setColor(mix(new Color(170, 190, 180), new Color(30, 36, 56), t, 255));
        g2d.fillOval((int) knobX, (int) (trackY + 2), (int) (trackH - 4), (int) (trackH - 4));
    }

    /** Draws a cartoon ghost centered on (cx, cy) with the given body width. */
    private void drawGhost(Graphics2D g2d, double cx, double cy, double size, Color body, Color eyes) {
        double w = size;
        double h = size * 1.15;
        double x = cx - w / 2;
        double y = cy - h / 2;

        Path2D p = new Path2D.Double();
        p.moveTo(x, y + h);
        p.lineTo(x, y + w / 2);
        p.curveTo(x, y - 0.167 * w, x + w, y - 0.167 * w, x + w, y + w / 2); // dome
        p.lineTo(x + w, y + h);
        double s = w / 3;
        for (int i = 0; i < 3; i++) {                                        // wavy hem
            p.quadTo(x + w - i * s - s / 2, y + h - 0.35 * w, x + w - (i + 1) * s, y + h);
        }
        p.closePath();

        g2d.setColor(body);
        g2d.fill(p);

        g2d.setColor(eyes);
        double eyeW = w * 0.13, eyeH = w * 0.19;
        g2d.fill(new java.awt.geom.Ellipse2D.Double(cx - w * 0.22 - eyeW / 2, y + w * 0.38, eyeW, eyeH));
        g2d.fill(new java.awt.geom.Ellipse2D.Double(cx + w * 0.22 - eyeW / 2, y + w * 0.38, eyeW, eyeH));
        g2d.fill(new java.awt.geom.Ellipse2D.Double(cx - w * 0.07, y + w * 0.68, w * 0.14, w * 0.16));
    }

    private Point2D calculatePeerPosition(PeerDevice peer, int cx, int cy, int maxR) {
        String ip = peer.getIpAddress() != null ? peer.getIpAddress().getHostAddress() : "127.0.01";
        int hashAngle = 0;
        int lastOctet = 20;

        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                lastOctet = Integer.parseInt(parts[3]);
                hashAngle = (Integer.parseInt(parts[2]) * 31 + lastOctet) % 360;
            }
        } catch (Exception ignored) {
            hashAngle = Math.abs(ip.hashCode() % 360);
        }
        double theta = Math.toRadians(hashAngle);
        // Distance normalized: between 30% and 90% radius based on IP octet / simulated latency
        double normalizedDist = 0.35 + ((lastOctet % 50) / 100.0);
        double r = maxR * Math.min(normalizedDist, 0.90);

        return new Point2D.Double(cx + r * Math.cos(theta), cy + r * Math.sin(theta));
    }
}