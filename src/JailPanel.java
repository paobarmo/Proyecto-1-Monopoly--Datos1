import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;

/**
 * The jail, shown apart from the board: it is not part of the circular
 * list. Its function (keeping a player for three turns) is not implemented
 * yet; for now it is only drawn.
 */
public class JailPanel extends JPanel {

    private static final int PANEL_SIZE = 200;
    private static final String LABEL = "Jail";

    public JailPanel() {
        setPreferredSize(new Dimension(PANEL_SIZE, PANEL_SIZE));
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

        g2.setFont(new Font("SansSerif", Font.BOLD, 36));
        FontMetrics metrics = g2.getFontMetrics();
        int textX = (width - metrics.stringWidth(LABEL)) / 2;
        int textY = (height - metrics.getHeight()) / 2 + metrics.getAscent();
        g2.drawString(LABEL, textX, textY);

        g2.dispose();
    }
}
