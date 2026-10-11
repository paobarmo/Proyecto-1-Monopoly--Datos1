import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

import javax.swing.JPanel;

/**
 * One square of the player selection screen: shows the player's name and,
 * below it, a circle in the player's color (placeholder for a future
 * avatar). An inactive slot is drawn dimmed, meaning that player is not
 * taking part in the game.
 */
public class PlayerSlotPanel extends JPanel {

    private static final int SIZE = 240;
    private static final int CIRCLE_DIAMETER = 90;

    private static final Color ACTIVE_BACKGROUND = new Color(220, 220, 220);
    private static final Color INACTIVE_BACKGROUND = new Color(105, 105, 105);
    private static final Color INACTIVE_FOREGROUND = new Color(160, 160, 160);

    private final String name;
    private final Color playerColor;
    private boolean active;
    private final int avatarPlayerIndex;

    public PlayerSlotPanel(String name, Color playerColor, boolean active, Runnable onClick) {
        this(name, playerColor, active, onClick, -1);
    }

    public PlayerSlotPanel(String name, Color playerColor, boolean active, Runnable onClick, int avatarPlayerIndex) {
        this.avatarPlayerIndex = avatarPlayerIndex;
        this.name = Objects.requireNonNull(name, "name cannot be null.");
        this.playerColor = Objects.requireNonNull(playerColor, "playerColor cannot be null.");
        this.active = active;
        Objects.requireNonNull(onClick, "onClick cannot be null.");

        setPreferredSize(new Dimension(SIZE, SIZE));
        setOpaque(false);

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

        // Tarjeta medieval con fondo oscuro y borde dorado.
        g2.setColor(active ? new Color(58, 42, 67) : new Color(43, 36, 51));
        g2.fillRoundRect(2, 2, width - 5, height - 5, 20, 20);
        g2.setColor(active ? new Color(202, 161, 104) : new Color(92, 78, 98));
        g2.drawRoundRect(2, 2, width - 5, height - 5, 20, 20);

        g2.setFont(new Font("Georgia", Font.BOLD, 25));
        g2.setColor(active ? new Color(250, 225, 184) : INACTIVE_FOREGROUND);
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(name, (width - metrics.stringWidth(name)) / 2, 49);

        int portraitSize = 137;
        int px = (width - portraitSize) / 2;
        int py = 65;
        g2.setColor(new Color(30, 26, 41));
        g2.fillRoundRect(px, py, portraitSize, portraitSize, 18, 18);
        g2.setColor(active ? new Color(146, 107, 113) : new Color(89, 77, 95));
        g2.drawRoundRect(px, py, portraitSize, portraitSize, 18, 18);

        Image avatar = avatarPlayerIndex >= 0
                ? AvatarSprites.forAvatar(AvatarSprites.choice(avatarPlayerIndex)) : null;
        if (avatar != null) {
            int iw = avatar.getWidth(this);
            int ih = avatar.getHeight(this);
            if (iw > 0 && ih > 0) {
                double scale = Math.min(124.0 / iw, 124.0 / ih);
                int dw = Math.max(1, (int) (iw * scale));
                int dh = Math.max(1, (int) (ih * scale));
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                if (!active) g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.38f));
                g2.drawImage(avatar, (width - dw) / 2, py + (portraitSize - dh) / 2, dw, dh, this);
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
            }
        } else {
            g2.setColor(active ? playerColor : INACTIVE_FOREGROUND);
            g2.fillOval((width - 75) / 2, 98, 75, 75);
        }

        g2.dispose();
    }
}
