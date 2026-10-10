import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JPanel;

/**
 * Draws the board in the center of the screen, reading every square from
 * the Board (circular list) and every player's position from the Game each
 * time it is painted. Call repaint() after any change.
 *
 * The board is a square whose side is 25 units (k pixels each): each corner
 * is 5x5 units and each of the 5 squares per side is 3 units wide and 5
 * deep (2*5 + 5*3 = 25). This requires the board to have exactly 24 squares.
 *
 * Property squares are white with a small strip of the group color on the
 * edge facing the center of the board; the name is written in the white
 * part. Event squares and corners are white with only their name.
 *
 * Players are drawn as circles of their color in a row along the outer edge
 * of the square they are standing on (the bottom edge in the corners).
 *
 * Index layout (clockwise, starting at the bottom-right corner):
 *   0 corner (bottom-right), 1-5 bottom row going left, 6 corner
 *   (bottom-left), 7-11 left column going up, 12 corner (top-left), 13-17 top
 *   row going right, 18 corner (top-right), 19-23 right column going down.
 */
public class BoardPanel extends JPanel {

    private static final int EXPECTED_SQUARES = 24;
    private static final int MARGIN = 20;
    private static final int BOARD_UNITS = 25;
    private static final int CORNER_UNITS = 5;
    private static final int CELL_UNITS = 3;
    private static final int MIN_FONT_SIZE = 8;
    private static final int TEXT_PADDING = 3;
    private static final int TOKEN_PADDING = 2;
    private static final int TOKEN_SPACING = 2;

    // A side of a square.
    private enum Side { NONE, TOP, RIGHT, BOTTOM, LEFT }

    private final Game game;
    private final Board board;

    // Used by the movement animation: while a player is being animated, they
    // are drawn on this square instead of the one the Board says they are on.
    private int visualPlayerIndex = -1;
    private int visualPosition = 0;

    public BoardPanel(Game game) {
        this.game = Objects.requireNonNull(game, "game cannot be null.");
        this.board = game.getBoard();

        if (board.getSize() != EXPECTED_SQUARES) {
            throw new IllegalArgumentException(
                    "BoardPanel requires exactly " + EXPECTED_SQUARES + " squares.");
        }
        setOpaque(false);
    }

    /**
     * Draws the given player on the given square, ignoring their real
     * position, until clearVisualPosition() is called. Used to show a move
     * square by square.
     */
    public void setVisualPosition(int playerIndex, int squareIndex) {
        this.visualPlayerIndex = playerIndex;
        this.visualPosition = squareIndex;
        repaint();
    }

    /**
     * Goes back to drawing every player on their real square.
     */
    public void clearVisualPosition() {
        this.visualPlayerIndex = -1;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int available = Math.min(getWidth(), getHeight()) - 2 * MARGIN;
        int unit = available / BOARD_UNITS;
        if (unit < 1) {
            return;
        }

        int side = unit * BOARD_UNITS;
        int originX = (getWidth() - side) / 2;
        int originY = (getHeight() - side) / 2;

        // Read every player's position once per paint.
        int[] positions = new int[game.getPlayerCount()];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = game.isEliminated(i) ? -1 : game.getPlayerPosition(i);
        }
        if (visualPlayerIndex >= 0 && visualPlayerIndex < positions.length
                && !game.isEliminated(visualPlayerIndex)) {
            positions[visualPlayerIndex] = visualPosition;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (int i = 0; i < board.getSize(); i++) {
            drawSquare(g2, board.getCasilla(i), i, originX, originY, unit, positions);
        }

        g2.dispose();
    }

    private void drawSquare(Graphics2D g2, Casilla casilla, int index,
                            int originX, int originY, int unit, int[] positions) {
        Rectangle bounds = boundsOf(index, originX, originY, unit);
        Side inward = inwardOf(index);

        // Band reserved for the player circles, on the outer edge.
        Side tokenSide = opposite(inward);
        int tokenBand = tokenDiameter(unit) + 2 * TOKEN_PADDING;

        g2.setColor(Color.WHITE);
        g2.fill(bounds);

        Rectangle textArea = withoutBand(bounds, tokenSide, tokenBand);

        if (casilla instanceof CasillaPropiedad) {
            CasillaPropiedad property = (CasillaPropiedad) casilla;
            int stripThickness = (int) Math.round(unit * 1.2);

            Rectangle strip = band(bounds, inward, stripThickness);
            g2.setColor(colorFor(property.getColorGroup()));
            g2.fill(strip);
            g2.setColor(Color.BLACK);
            g2.draw(strip);
            drawOwnerMarker(g2, strip, property);

            textArea = withoutBand(textArea, inward, stripThickness);
        }

        g2.setColor(Color.BLACK);
        g2.draw(bounds);

        drawWrappedText(g2, casilla.getName(), textArea, Math.max(MIN_FONT_SIZE, unit / 2));
        drawTokens(g2, band(bounds, tokenSide, tokenBand), index, positions, unit);
    }

    // ------------------------------------------------------------- geometry

    private Rectangle boundsOf(int index, int originX, int originY, int unit) {
        int corner = CORNER_UNITS * unit;
        int cell = CELL_UNITS * unit;
        int far = BOARD_UNITS * unit - corner; // offset of the far corners

        if (index == 0) {
            return new Rectangle(originX + far, originY + far, corner, corner);
        }
        if (index <= 5) {
            int j = index - 1;
            return new Rectangle(originX + far - (j + 1) * cell, originY + far, cell, corner);
        }
        if (index == 6) {
            return new Rectangle(originX, originY + far, corner, corner);
        }
        if (index <= 11) {
            int j = index - 7;
            return new Rectangle(originX, originY + far - (j + 1) * cell, corner, cell);
        }
        if (index == 12) {
            return new Rectangle(originX, originY, corner, corner);
        }
        if (index <= 17) {
            int j = index - 13;
            return new Rectangle(originX + corner + j * cell, originY, cell, corner);
        }
        if (index == 18) {
            return new Rectangle(originX + far, originY, corner, corner);
        }
        int j = index - 19;
        return new Rectangle(originX + far, originY + corner + j * cell, corner, cell);
    }

    // Side of the square that faces the center of the board.
    private Side inwardOf(int index) {
        if (index >= 1 && index <= 5) {
            return Side.TOP;
        }
        if (index >= 7 && index <= 11) {
            return Side.RIGHT;
        }
        if (index >= 13 && index <= 17) {
            return Side.BOTTOM;
        }
        if (index >= 19 && index <= 23) {
            return Side.LEFT;
        }
        return Side.NONE;
    }

    // Opposite side; corners (no inward side) use the bottom.
    private Side opposite(Side side) {
        switch (side) {
            case TOP:
                return Side.BOTTOM;
            case BOTTOM:
                return Side.TOP;
            case LEFT:
                return Side.RIGHT;
            case RIGHT:
                return Side.LEFT;
            default:
                return Side.BOTTOM;
        }
    }

    // The band of the given thickness along one side of the rectangle.
    private Rectangle band(Rectangle b, Side side, int thickness) {
        switch (side) {
            case TOP:
                return new Rectangle(b.x, b.y, b.width, thickness);
            case BOTTOM:
                return new Rectangle(b.x, b.y + b.height - thickness, b.width, thickness);
            case LEFT:
                return new Rectangle(b.x, b.y, thickness, b.height);
            case RIGHT:
                return new Rectangle(b.x + b.width - thickness, b.y, thickness, b.height);
            default:
                throw new IllegalArgumentException("A side is required.");
        }
    }

    // The rectangle without the band of the given thickness along one side.
    private Rectangle withoutBand(Rectangle b, Side side, int thickness) {
        switch (side) {
            case TOP:
                return new Rectangle(b.x, b.y + thickness, b.width, b.height - thickness);
            case BOTTOM:
                return new Rectangle(b.x, b.y, b.width, b.height - thickness);
            case LEFT:
                return new Rectangle(b.x + thickness, b.y, b.width - thickness, b.height);
            case RIGHT:
                return new Rectangle(b.x, b.y, b.width - thickness, b.height);
            default:
                throw new IllegalArgumentException("A side is required.");
        }
    }

    // --------------------------------------------------------------- colors

    private Color colorFor(String colorGroup) {
        switch (colorGroup) {
            case "Light Blue":
                return new Color(170, 224, 250);
            case "Pink":
                return new Color(255, 105, 180);
            case "Orange":
                return new Color(255, 140, 0);
            case "Yellow":
                return new Color(255, 221, 0);
            default:
                return Color.LIGHT_GRAY;
        }
    }

    // --------------------------------------------------------------- tokens

    // Small circle, in the owner's color, on the color strip of an owned property.
    private void drawOwnerMarker(Graphics2D g2, Rectangle strip, CasillaPropiedad property) {
        Player owner = property.getOwner();
        if (owner == null) {
            return;
        }

        int ownerIndex = game.indexOf(owner);
        if (ownerIndex < 0) {
            return;
        }

        int diameter = Math.max(6, (int) (Math.min(strip.width, strip.height) * 0.7));
        int x = strip.x + (strip.width - diameter) / 2;
        int y = strip.y + (strip.height - diameter) / 2;

        if (!Decoration.paintAvatar(g2, ownerIndex, x, y, diameter)) {
            g2.setColor(PlayerColors.forPlayer(ownerIndex));
            g2.fillOval(x, y, diameter, diameter);
        }
        g2.setColor(Color.BLACK);
        g2.drawOval(x, y, diameter, diameter);
    }

    private int tokenDiameter(int unit) {
        return Math.max(10, (int) (unit * 1.1));
    }

    /**
     * Draws the token (the player's image in a circle, or a circle in the
     * player's color if there is no image) for every player standing on the
     * given square, in a centered row (or column) inside the area. If they do
     * not all fit, they overlap a little.
     */
    private void drawTokens(Graphics2D g2, Rectangle area, int squareIndex,
                            int[] positions, int unit) {
        List<Integer> playersHere = new ArrayList<>();
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] == squareIndex) {
                playersHere.add(i);
            }
        }
        if (playersHere.isEmpty()) {
            return;
        }

        int diameter = tokenDiameter(unit);
        int count = playersHere.size();
        boolean horizontal = area.width >= area.height;

        // Distance between the starts of two neighboring tokens; smaller than
        // the diameter (overlapping) when the row would not fit in the square.
        int length = (horizontal ? area.width : area.height) - 2 * TOKEN_PADDING;
        int step = diameter + TOKEN_SPACING;
        if (count > 1 && (count - 1) * step + diameter > length) {
            step = Math.max(1, (length - diameter) / (count - 1));
        }
        int totalLength = (count - 1) * step + diameter;

        int startX = horizontal
                ? area.x + (area.width - totalLength) / 2
                : area.x + (area.width - diameter) / 2;
        int startY = horizontal
                ? area.y + (area.height - diameter) / 2
                : area.y + (area.height - totalLength) / 2;

        for (int n = 0; n < count; n++) {
            int x = horizontal ? startX + n * step : startX;
            int y = horizontal ? startY : startY + n * step;

            int playerIndex = playersHere.get(n);
            if (!Decoration.paintAvatar(g2, playerIndex, x, y, diameter)) {
                g2.setColor(PlayerColors.forPlayer(playerIndex));
                g2.fillOval(x, y, diameter, diameter);
            }
            g2.setColor(Color.BLACK);
            g2.drawOval(x, y, diameter, diameter);
        }
    }

    // ----------------------------------------------------------------- text

    /**
     * Draws the text centered in the area, wrapping it into several lines
     * and shrinking the font until it fits.
     */
    private void drawWrappedText(Graphics2D g2, String text, Rectangle area, int baseFontSize) {
        int maxWidth = area.width - 2 * TEXT_PADDING;
        int maxHeight = area.height - 2 * TEXT_PADDING;

        int fontSize = baseFontSize;
        FontMetrics metrics;
        List<String> lines;

        while (true) {
            g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            metrics = g2.getFontMetrics();
            lines = wrap(text, metrics, maxWidth);

            if (fits(lines, metrics, maxWidth, maxHeight) || fontSize <= MIN_FONT_SIZE) {
                break;
            }
            fontSize--;
        }

        int totalHeight = lines.size() * metrics.getHeight();
        int y = area.y + (area.height - totalHeight) / 2 + metrics.getAscent();

        g2.setColor(Color.BLACK);
        for (String line : lines) {
            int x = area.x + (area.width - metrics.stringWidth(line)) / 2;
            g2.drawString(line, x, y);
            y += metrics.getHeight();
        }
    }

    private List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String current = "";

        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;

            if (!current.isEmpty() && metrics.stringWidth(candidate) > maxWidth) {
                lines.add(current);
                current = word;
            } else {
                current = candidate;
            }
        }

        if (!current.isEmpty()) {
            lines.add(current);
        }
        return lines;
    }

    private boolean fits(List<String> lines, FontMetrics metrics, int maxWidth, int maxHeight) {
        if (lines.size() * metrics.getHeight() > maxHeight) {
            return false;
        }
        for (String line : lines) {
            if (metrics.stringWidth(line) > maxWidth) {
                return false;
            }
        }
        return true;
    }
}
