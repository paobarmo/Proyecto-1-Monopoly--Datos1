import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import javax.swing.ImageIcon;
import java.net.URL;
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
 * A purchased property gets a border in the color of its owner.
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

    // Solo imagen decorativa en el centro del tablero (no afecta casillas ni reglas).
    private final Image centerBackground;


    // Used by the movement animation: while a player is being animated, they
    // are drawn on this square instead of the one the Board says they are on.
    private int visualPlayerIndex = -1;
    private int visualPosition = 0;
    private int[] networkPositions;
    private int[] networkOwners;
    private int[] networkJailTurns;
    private boolean[] networkActive;

    public BoardPanel(Game game) {
        this.game = Objects.requireNonNull(game, "game cannot be null.");
        this.board = game.getBoard();
        URL backgroundUrl = BoardPanel.class.getResource("/images/fondo_centro.jpg");
        centerBackground = backgroundUrl == null ? null : new ImageIcon(backgroundUrl).getImage();

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

    /** Solo presentación: el servidor es dueño de las posiciones. */
    public void setNetworkPositions(int[] positions) {
        this.networkPositions = positions == null ? null : positions.clone();
        repaint();
    }

    /**
     * Solo presentación: dueño de cada casilla según el servidor (-1 si no
     * tiene). Mientras no se llame, se leen los dueños del Game local.
     */
    public void setNetworkOwners(int[] owners) {
        this.networkOwners = owners == null ? null : owners.clone();
        repaint();
    }

    /**
     * Solo presentación: turnos de cárcel que le faltan a cada jugador según
     * el servidor. Mientras no se llame, se leen del Game local.
     */
    public void setNetworkJailTurns(int[] turns) {
        this.networkJailTurns = turns == null ? null : turns.clone();
        repaint();
    }

    /** Solo presentación: qué jugadores siguen en la partida según el servidor. */
    public void setNetworkActive(boolean[] active) {
        this.networkActive = active == null ? null : active.clone();
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
            positions[i] = game.getPlayerPosition(i);
        }
        if (networkPositions != null && networkPositions.length == positions.length) {
            System.arraycopy(networkPositions, 0, positions, 0, positions.length);
        }
        // Players in jail are drawn in the JailPanel, not on the board. The player
        // being animated is still shown until the animation ends.
        for (int i = 0; i < positions.length; i++) {
            boolean jailed = networkJailTurns != null && i < networkJailTurns.length
                    ? networkJailTurns[i] > 0
                    : game.isInJail(i);
            if (jailed) {
                positions[i] = -1;
            }
        }
        // Eliminated players disappear from the board (the one being animated stays
        // visible until the animation ends).
        for (int i = 0; i < positions.length; i++) {
            boolean gone = networkActive != null && i < networkActive.length
                    ? !networkActive[i]
                    : game.isEliminated(i);
            if (gone) {
                positions[i] = -1;
            }
        }
        if (visualPlayerIndex >= 0 && visualPlayerIndex < positions.length) {
            positions[visualPlayerIndex] = visualPosition;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawCenterBackground(g2, originX, originY, unit);

        for (int i = 0; i < board.getSize(); i++) {
            drawSquare(g2, board.getCasilla(i), i, originX, originY, unit, positions);
        }

        g2.dispose();
    }

    // Rellena únicamente el hueco central de 15 x 15 unidades.
    // Recorta la fotografía al centro para conservar sus proporciones.
    private void drawCenterBackground(Graphics2D g2, int originX, int originY, int unit) {
        if (centerBackground == null) return;
        int imageWidth = centerBackground.getWidth(this);
        int imageHeight = centerBackground.getHeight(this);
        if (imageWidth <= 0 || imageHeight <= 0) return;

        int x = originX + CORNER_UNITS * unit;
        int y = originY + CORNER_UNITS * unit;
        int size = (BOARD_UNITS - 2 * CORNER_UNITS) * unit;
        int crop = Math.min(imageWidth, imageHeight);
        int sourceX = (imageWidth - crop) / 2;
        int sourceY = (imageHeight - crop) / 2;
        g2.drawImage(centerBackground, x, y, x + size, y + size,
                sourceX, sourceY, sourceX + crop, sourceY + crop, this);
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

            textArea = withoutBand(textArea, inward, stripThickness);
        }

        g2.setColor(Color.BLACK);
        g2.draw(bounds);

        drawOwnerMark(g2, casilla, index, bounds, unit);

        drawWrappedText(g2, casilla.getName(), textArea, Math.max(MIN_FONT_SIZE, unit / 2));
        drawTokens(g2, band(bounds, tokenSide, tokenBand), index, positions, unit);
    }

    // ---------------------------------------------------------------- owners

    // Marks a purchased property with a border in its owner's color.
    private void drawOwnerMark(Graphics2D g2, Casilla casilla, int index, Rectangle bounds, int unit) {
        int ownerIndex = ownerIndexOf(casilla, index);
        if (ownerIndex < 0) {
            return;
        }

        float thickness = Math.max(3f, unit / 5f);
        int inset = Math.round(thickness / 2f) + 1;
        java.awt.Stroke previous = g2.getStroke();
        g2.setStroke(new BasicStroke(thickness));
        g2.setColor(PlayerColors.forPlayer(ownerIndex));
        g2.drawRect(bounds.x + inset, bounds.y + inset,
                bounds.width - 2 * inset, bounds.height - 2 * inset);
        g2.setStroke(previous);
    }

    // Owner of a square: from the server in a network game, else from the local Game.
    private int ownerIndexOf(Casilla casilla, int index) {
        if (!(casilla instanceof CasillaPropiedad)) {
            return -1;
        }
        if (networkOwners != null) {
            return index < networkOwners.length ? networkOwners[index] : -1;
        }
        return game.getPlayerIndex(((CasillaPropiedad) casilla).getOwner());
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

    private int tokenDiameter(int unit) {
        // Ligeramente mayor que antes, sin alterar el tamaño de las casillas.
        return Math.max(20, (int) (unit * 1.08));
    }

    /** Dibuja los avatares como fichas redondas, solo en el tablero. */
    private void drawTokens(Graphics2D g2, Rectangle area, int squareIndex,
                            int[] positions, int unit) {
        List<Integer> playersHere = new ArrayList<>();
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] == squareIndex) {
                playersHere.add(i);
            }
        }
        if (playersHere.isEmpty()) return;

        int count = playersHere.size();
        boolean horizontal = area.width >= area.height;
        int availableLength = horizontal ? area.width : area.height;
        int availableThickness = horizontal ? area.height : area.width;

        // Evita que se amontonen las fichas cuando coinciden varios jugadores.
        int diameter = Math.min(tokenDiameter(unit),
                Math.min(Math.max(8, availableThickness - 4),
                        Math.max(8, (availableLength - (count - 1) * TOKEN_SPACING - 4) / count)));
        int step = diameter + TOKEN_SPACING;
        int totalLength = count * diameter + (count - 1) * TOKEN_SPACING;
        int startX = horizontal ? area.x + (area.width - totalLength) / 2
                : area.x + (area.width - diameter) / 2;
        int startY = horizontal ? area.y + (area.height - diameter) / 2
                : area.y + (area.height - totalLength) / 2;

        for (int n = 0; n < count; n++) {
            int x = horizontal ? startX + n * step : startX;
            int y = horizontal ? startY : startY + n * step;
            int player = playersHere.get(n);

            Graphics2D token = (Graphics2D) g2.create();
            token.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // La imagen sigue dibujándose mediante AvatarSprites: no cambia la selección.
            Shape circle = new Ellipse2D.Double(x, y, diameter, diameter);
            token.setColor(new Color(12, 8, 22, 100));
            token.fillOval(x + 1, y + 2, diameter, diameter);
            token.setColor(new Color(47, 31, 57));
            token.fill(circle);
            token.setClip(circle);
            AvatarSprites.draw(token, player, x, y, diameter);
            token.setClip(null);
            token.setStroke(new BasicStroke(Math.max(1f, diameter * 0.075f)));
            token.setColor(new Color(219, 178, 111));
            token.draw(new Ellipse2D.Double(x + 1, y + 1, diameter - 2, diameter - 2));
            token.dispose();
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
