import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

import javax.swing.JPanel;

/**
 * One square of the player selection screen: shows the player's name and,
 * below it, the player's token image in a circle (a circle in the player's
 * color if the image is not available). An inactive slot is drawn dimmed,
 * meaning that player is not taking part in the game.
 */
public class PlayerSlotPanel extends JPanel {

    private static final int SIZE = 240;
    private static final int CIRCLE_DIAMETER = 110;

    private static final Color ACTIVE_BACKGROUND = new Color(220, 220, 220);
    private static final Color INACTIVE_BACKGROUND = new Color(105, 105, 105);
    private static final Color INACTIVE_FOREGROUND = new Color(160, 160, 160);

    private final String name;
    private final Color playerColor;
    private final int playerIndex;
    private boolean active;

    public PlayerSlotPanel(String name, Color playerColor, int playerIndex, boolean active,
                           Runnable onClick) {
        this.name = Objects.requireNonNull(name, "name cannot be null.");
        this.playerColor = Objects.requireNonNull(playerColor, "playerColor cannot be null.");
        this.playerIndex = playerIndex;
        this.active = active;
        Objects.requireNonNull(onClick, "onClick cannot be null.");

        setPreferredSize(new Dimension(SIZE, SIZE));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.run();
            }
        });
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Square background and border.
        g2.setColor(active ? ACTIVE_BACKGROUND : INACTIVE_BACKGROUND);
        g2.fillRect(0, 0, width, height);
        g2.setColor(Color.BLACK);
        g2.drawRect(0, 0, width - 1, height - 1);

        // Player name, centered near the top.
        g2.setFont(new Font("SansSerif", Font.BOLD, 28));
        g2.setColor(active ? Color.BLACK : INACTIVE_FOREGROUND);
        FontMetrics metrics = g2.getFontMetrics();
        int textX = (width - metrics.stringWidth(name)) / 2;
        int textY = (int) (height * 0.26);
        g2.drawString(name, textX, textY);

        // Token below the name: the image (dimmed and gray when inactive).
        int circleX = (width - CIRCLE_DIAMETER) / 2;
        int circleY = (int) (height * 0.38);

        if (!active) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        }

        boolean hasImage = Decoration.paintAvatar(g2, playerIndex, circleX, circleY,
                CIRCLE_DIAMETER, !active);
        if (!hasImage && active) {
            g2.setColor(playerColor);
            g2.fillOval(circleX, circleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
        }

        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(active ? Color.BLACK : INACTIVE_FOREGROUND);
        g2.drawOval(circleX, circleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);

        g2.dispose();
    }
}
