package nes.gui;

import nes.Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class Display extends JPanel {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 240;
    private static final int SCALE = 3;

    private final BufferedImage image;
    private final int[] pixels;
    private Controller controller;

    public Display() {
        // Create a Window
        JFrame frame = new JFrame("Java NES Emulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());
        frame.add(this, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Initialize Image Buffer
        image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        // Input Handling
        this.setFocusable(true);
        this.requestFocusInWindow();
        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (controller == null)
                    return;
                updateController(e.getKeyCode(), true);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (controller == null)
                    return;
                updateController(e.getKeyCode(), false);
            }
        });
    }

    public void setController(Controller controller) {
        this.controller = controller;
    }

    private void updateController(int keyCode, boolean pressed) {
        switch (keyCode) {
            case KeyEvent.VK_Z:
                controller.setButtonPressed(0, pressed);
                break; // A
            case KeyEvent.VK_X:
                controller.setButtonPressed(1, pressed);
                break; // B
            case KeyEvent.VK_SHIFT:
                controller.setButtonPressed(2, pressed);
                break; // Select
            case KeyEvent.VK_ENTER:
                controller.setButtonPressed(3, pressed);
                break; // Start
            case KeyEvent.VK_UP:
                controller.setButtonPressed(4, pressed);
                break; // Up
            case KeyEvent.VK_DOWN:
                controller.setButtonPressed(5, pressed);
                break; // Down
            case KeyEvent.VK_LEFT:
                controller.setButtonPressed(6, pressed);
                break; // Left
            case KeyEvent.VK_RIGHT:
                controller.setButtonPressed(7, pressed);
                break; // Right
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH * SCALE, HEIGHT * SCALE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
    }

    public void setPixel(int x, int y, int color) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            pixels[y * WIDTH + x] = color;
        }
    }

    public void refresh() {
        repaint();
    }
}
