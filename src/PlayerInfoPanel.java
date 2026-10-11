import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;

import javax.swing.JPanel;

/**
 * Rectangle shown in a screen corner for each player: color circle, name and
 * balance. The balance is read from the Bank every time the panel is
 * painted, so calling repaint() is enough to show an updated amount.
 */
public class PlayerInfoPanel extends JPanel {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 120;
    private static final int CIRCLE_DIAMETER = 56;
    private static final int PADDING = 20;
    private static final Color BACKGROUND = new Color(52, 38, 62);
    private static final Color GOLD = new Color(222, 180, 116);
    private static final Color CREAM = new Color(248, 226, 197);

    private final Player player;
    private final String name;
    private final Color playerColor;
    private final Bank bank;
    private String networkName;
    private Integer networkBalance;
    private Boolean networkActive;
    private int avatarPlayerIndex = -1;

    public void setAvatarPlayerIndex(int index) { avatarPlayerIndex = index; repaint(); }

    public PlayerInfoPanel(Player player, String name, Color playerColor, Bank bank) {
        this.player = Objects.requireNonNull(player, "player cannot be null.");
        this.name = Objects.requireNonNull(name, "name cannot be null.");
        this.playerColor = Objects.requireNonNull(playerColor, "playerColor cannot be null.");
        this.bank = Objects.requireNonNull(bank, "bank cannot be null.");

        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
    }

    /** Sobrescribe solo lo mostrado, no modifica el banco local. */
    public void setNetworkState(String newName, int balance, boolean active) {
        networkName = newName;
        networkBalance = balance;
        networkActive = active;
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

        g2.setColor(BACKGROUND);
        g2.fillRoundRect(1, 1, width - 3, height - 3, 20, 20);
        g2.setColor(GOLD);
        g2.setStroke(new java.awt.BasicStroke(2f));
        g2.drawRoundRect(2, 2, width - 5, height - 5, 20, 20);

        int circleY = (height - CIRCLE_DIAMETER) / 2;
        if (avatarPlayerIndex >= 0) {
            AvatarSprites.draw(g2, avatarPlayerIndex, PADDING, circleY, CIRCLE_DIAMETER);
        } else {
            g2.setColor(playerColor);
            g2.fillOval(PADDING, circleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
            g2.setColor(Color.BLACK);
            g2.drawOval(PADDING, circleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
        }

        int textX = PADDING + CIRCLE_DIAMETER + 16;

        g2.setColor(GOLD);
        g2.setFont(new Font("Georgia", Font.BOLD, 21));
        g2.drawString(networkName == null ? name : networkName, textX, (int) (height * 0.42));

        g2.setColor(CREAM);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g2.drawString("Balance: $" + (networkBalance == null ? bank.getBalance(player) : networkBalance),
                textX, (int) (height * 0.72));
        if (Boolean.FALSE.equals(networkActive)) {
            g2.setColor(new Color(255, 147, 143));
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.drawString("ELIMINADO", textX, height - 5);
        }

        g2.dispose();
    }
}
