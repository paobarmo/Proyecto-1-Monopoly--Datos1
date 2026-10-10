import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import javax.swing.JPanel;

/**
 * Rectangle shown in a screen corner for each player: token image in a
 * circle (a circle in the player's color if the image is not available),
 * name and balance. The balance is read from the Bank every time the panel
 * is painted, so calling repaint() is enough to show an updated amount.
 *
 * Once the player is eliminated, the image (or circle) and the name turn
 * gray and the balance is replaced by the word "Eliminado".
 */
public class PlayerInfoPanel extends JPanel {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 120;
    private static final int CIRCLE_DIAMETER = 76;
    private static final int PADDING = 20;

    private static final Color BACKGROUND = new Color(220, 220, 220);
    private static final Color ELIMINATED_BACKGROUND = new Color(175, 175, 175);
    private static final Color ELIMINATED_CIRCLE = new Color(130, 130, 130);
    private static final Color ELIMINATED_TEXT = new Color(110, 110, 110);

    private final Player player;
    private final int playerIndex;
    private final String name;
    private final Color playerColor;
    private final Bank bank;
    private final BooleanSupplier eliminated;

    public PlayerInfoPanel(Player player, int playerIndex, String name, Color playerColor,
                           Bank bank, BooleanSupplier eliminated) {
        this.player = Objects.requireNonNull(player, "player cannot be null.");
        this.playerIndex = playerIndex;
        this.name = Objects.requireNonNull(name, "name cannot be null.");
        this.playerColor = Objects.requireNonNull(playerColor, "playerColor cannot be null.");
        this.bank = Objects.requireNonNull(bank, "bank cannot be null.");
        this.eliminated = Objects.requireNonNull(eliminated, "eliminated cannot be null.");

        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        boolean out = eliminated.getAsBoolean();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        g2.setColor(out ? ELIMINATED_BACKGROUND : BACKGROUND);
        g2.fillRect(0, 0, width, height);
        g2.setColor(Color.BLACK);
        g2.drawRect(0, 0, width - 1, height - 1);

        int circleY = (height - CIRCLE_DIAMETER) / 2;
        boolean hasImage = Decoration.paintAvatar(g2, playerIndex, PADDING, circleY,
                CIRCLE_DIAMETER, out);
        if (!hasImage) {
            g2.setColor(out ? ELIMINATED_CIRCLE : playerColor);
            g2.fillOval(PADDING, circleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
        }
        g2.setColor(out ? ELIMINATED_TEXT : Color.BLACK);
        g2.drawOval(PADDING, circleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);

        int textX = PADDING + CIRCLE_DIAMETER + 14;
        g2.setColor(out ? ELIMINATED_TEXT : Color.BLACK);

        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.drawString(name, textX, (int) (height * 0.42));

        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        String secondLine = out ? "Eliminado" : "Balance: $" + bank.getBalance(player);
        g2.drawString(secondLine, textX, (int) (height * 0.72));

        g2.dispose();
    }
}
