import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

/**
 * The jail, shown apart from the board: it is not part of the circular
 * list. The players who are in jail are drawn inside it, each one with the
 * number of turns they still have to lose.
 */
public class JailPanel extends JPanel {

    private static final int PANEL_SIZE = 200;
    private static final String LABEL = "Jail";
    private static final int MAX_TOKEN_DIAMETER = 46;
    private static final int TOKEN_GAP = 8;
    private static final int SIDE_PADDING = 12;

    // Turns each player still has to lose in jail (index = player index; 0 = free).
    private int[] jailTurns = new int[0];

    public JailPanel() {
        setPreferredSize(new Dimension(PANEL_SIZE, PANEL_SIZE));
    }

    /**
     * Sets the players shown in the jail: every player whose value is greater
     * than 0 is drawn, with that many turns left.
     */
    public void setJailTurns(int[] turns) {
        this.jailTurns = turns == null ? new int[0] : turns.clone();
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

        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(4));
        g2.drawRect(2, 2, width - 5, height - 5);

        g2.setFont(new Font("SansSerif", Font.BOLD, 32));
        FontMetrics metrics = g2.getFontMetrics();
        int textX = (width - metrics.stringWidth(LABEL)) / 2;
        int textY = (int) (height * 0.30) + metrics.getAscent() / 2;
        g2.drawString(LABEL, textX, textY);

        drawPrisoners(g2, width, height);

        g2.dispose();
    }

    private void drawPrisoners(Graphics2D g2, int width, int height) {
        List<Integer> prisoners = new ArrayList<>();
        for (int i = 0; i < jailTurns.length; i++) {
            if (jailTurns[i] > 0) {
                prisoners.add(i);
            }
        }
        if (prisoners.isEmpty()) {
            return;
        }

        int count = prisoners.size();
        int available = width - 2 * SIDE_PADDING - (count - 1) * TOKEN_GAP;
        int diameter = Math.max(16, Math.min(MAX_TOKEN_DIAMETER, available / count));
        int totalWidth = count * diameter + (count - 1) * TOKEN_GAP;
        int startX = (width - totalWidth) / 2;
        int y = (int) (height * 0.50);

        Font turnsFont = new Font("SansSerif", Font.BOLD, 16);

        for (int n = 0; n < count; n++) {
            int player = prisoners.get(n);
            int x = startX + n * (diameter + TOKEN_GAP);

            // Same look as the tokens on the board.
            Graphics2D token = (Graphics2D) g2.create();
            Ellipse2D.Double circle = new Ellipse2D.Double(x, y, diameter, diameter);
            token.setColor(new Color(47, 31, 57));
            token.fill(circle);
            token.setClip(circle);
            AvatarSprites.draw(token, player, x, y, diameter);
            token.setClip(null);
            token.setStroke(new BasicStroke(Math.max(1f, diameter * 0.075f)));
            token.setColor(new Color(219, 178, 111));
            token.draw(circle);
            token.dispose();

            // Turns left, centered under the token.
            String text = String.valueOf(jailTurns[player]);
            g2.setFont(turnsFont);
            g2.setColor(Color.BLACK);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, x + (diameter - fm.stringWidth(text)) / 2,
                    y + diameter + fm.getAscent() + 3);
        }

        String caption = "turnos restantes";
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(Color.DARK_GRAY);
        FontMetrics captionMetrics = g2.getFontMetrics();
        g2.drawString(caption, (width - captionMetrics.stringWidth(caption)) / 2,
                y + diameter + 38);
    }
}
