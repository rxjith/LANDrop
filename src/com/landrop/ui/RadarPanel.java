package com.landrop.ui;

import com.landrop.model.PeerDevice;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.Collections;

public class RadarPanel extends JPanel {

    private Collection<PeerDevice> peers = Collections.emptyList();
    private double sweepAngle = 0.0;
    private final Timer sweepTimer;

    public RadarPanel() {
        setBackground(new Color(18,22,28));

        sweepTimer = new Timer(16, e -> {
            sweepAngle = (sweepAngle + 0.04) % (2 * Math.PI);
            repaint();
        });
        sweepTimer.start();
    }
    
    public void updatePeers(Collection<PeerDevice> activePeers) {
        this.peers = activePeers;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Enable anti-aliasing for smoother graphics
        
        int width = getWidth(); 
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        int maxRadius = Math.min(centerX, centerY) - 20;

        if(maxRadius <= 10) {       // Avoid drawing if the panel is too small
            g2d.dispose();
            return;
        }

        g2d.setColor(new Color(35,75,55));      // Set a darker green color for the radar circles
        g2d.setStroke(new BasicStroke(1.2f));   // Set a slightly thicker stroke for the radar circles
        for(int r = maxRadius/4; r<= maxRadius; r += maxRadius/4) {
            g2d.drawOval(centerX -r, centerY - r, 2*r, 2*r);    // Draw concentric circles for the radar
        }
        g2d.drawLine(centerX - maxRadius, centerY, centerX + maxRadius, centerY); // Horizontal line
        g2d.drawLine(centerX, centerY - maxRadius, centerX, centerY + maxRadius); // Vertical line

        int sweepX = centerX + (int) (maxRadius * Math.cos(sweepAngle));    // Calculate the x-coordinate of the sweep line based on the current sweep angle
        int sweepY = centerY + (int) (maxRadius * Math.sin(sweepAngle));    // Calculate the y-coordinate of the sweep line based on the current sweep angle
        g2d.setPaint(new GradientPaint(centerX, centerY, new Color(50, 220, 120, 180), sweepX, sweepY, new Color(20, 160, 80, 20))); // Create a gradient paint for the sweep line
        g2d.setStroke(new BasicStroke(2.0f)); // Set a thicker stroke for the sweep line
        g2d.drawLine(centerX, centerY, sweepX, sweepY); // Draw the sweep line

        for (PeerDevice peer : peers) {
            Point2D pt = calculatePeerPosition(peer, centerX, centerY, maxRadius); // Calculate the position of the peer on the radar
            int px = (int)pt.getX();
            int py = (int)pt.getY();

            g2d.setColor(new Color(0, 255,150, 70)); // Set a semi-transparent green color for the peer
            g2d.fillOval(px - 9, py - 9, 18, 18); // Draw a filled circle for the peer

            g2d.setColor(new Color(0, 255, 170));
            g2d.fillOval(px - 4, py - 4, 8, 8); // Draw a smaller filled circle for the center blip

            g2d.setColor(new Color(210, 230, 220)); // Set a light color for the peer's hostname text
            g2d.setFont(new Font("Segoe UI", Font.BOLD, 11)); // Set the font for the peer's hostname text
            g2d.drawString(peer.getHostname(), px + 8, py - 2); // Draw the peer's hostname next to the peer's position
        }
    g2d.dispose();
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

