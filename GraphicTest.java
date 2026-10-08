import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;

import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * Crude, utility-free graphical check that the Board data can be drawn.
 * No interaction, no images, no layout manager beyond a fixed grid: the
 * goal is only to confirm the board can be rendered.
 *
 * The board built by BoardFactory has exactly 20 squares (4 corners + 4
 * non-corner squares per side), which maps perfectly onto the perimeter of
 * a 6x6 grid (6*4 - 4 = 20 perimeter cells). The interior 4x4 cells are
 * left empty (just the gray background), mirroring the empty middle of a
 * real Monopoly board.
 *
 * Each square is read directly from Board at paint time (via getCasilla);
 * no separate structure associates a "graphic square" with its Node, since
 * squares never change position once the board is built.
 */
public class GraphicTest extends JPanel {

    private static final int CELL_SIZE = 80;
    private static final int GRID_SIZE = 6;

    private final Board board;

    private CartaEvento cartaMostrada;   // null

    public GraphicTest(Board board) {
        this.board = board;
        setPreferredSize(new Dimension(CELL_SIZE * GRID_SIZE, CELL_SIZE * GRID_SIZE));
        setBackground(Color.GRAY);

        CasillaEvento.setAlMostrarCarta(carta -> {
            cartaMostrada = carta;
            repaint();
        });

        addMouseListener(new java.awt.event.MouseAdapter() { // clic para cerrar carta
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                cartaMostrada = null;
                repaint();
            }
        });
    }

    private static final int TOKEN_SIZE = 20;
    private static final Color[] TOKEN_COLORS = {
            Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA
    };

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        for (int i = 0; i < board.getSize(); i++) {
            Casilla casilla = board.getCasilla(i);
            Point gridPosition = gridPositionFor(i);
            drawCasilla(g, casilla, gridPosition.x, gridPosition.y);
        }

        drawPlayers(g);   // después de las casillas, para quedar encima
        drawCarta(g);
    }

    private void drawPlayers(Graphics g) {
        // Contador por casilla, para que varios jugadores en la misma
        // casilla no se dibujen uno encima del otro.
        java.util.Map<Integer, Integer> countPerSquare = new java.util.HashMap<>();

        for (Player player : board.getPlayers()) {
            int index = board.getCurrentCasilla(player).getPosition();
            Point grid = gridPositionFor(index);

            int slot = countPerSquare.merge(index, 1, Integer::sum) - 1;

            // Desplazamiento dentro de la casilla (2 columnas x 2 filas)
            int offsetX = 8 + (slot % 2) * (TOKEN_SIZE + 6);
            int offsetY = 8 + (slot / 2) * (TOKEN_SIZE + 6);

            int x = grid.x * CELL_SIZE + offsetX;
            int y = grid.y * CELL_SIZE + offsetY;

            g.setColor(TOKEN_COLORS[player.id % TOKEN_COLORS.length]);
            g.fillOval(x, y, TOKEN_SIZE, TOKEN_SIZE);
            g.setColor(Color.BLACK);
            g.drawOval(x, y, TOKEN_SIZE, TOKEN_SIZE);
        }
    }

    /**
     * Maps a board index to a (column, row) position on the 6x6 grid.
     * Specific to this exact 20-square layout (bottom row right-to-left,
     * left column bottom-to-top, top row left-to-right, right column
     * top-to-bottom), matching the order squares were added in BoardFactory.
     */
    private Point gridPositionFor(int index) {
        if (index >= 0 && index <= 5) {
            // Bottom row: Go (col 5) down to Jail (col 0).
            return new Point(5 - index, 5);
        } else if (index >= 6 && index <= 10) {
            // Left column: going up to Free Parking (row 0).
            return new Point(0, 10 - index);
        } else if (index >= 11 && index <= 15) {
            // Top row: going right to Go To Jail (col 5).
            return new Point(index - 10, 0);
        } else if (index >= 16 && index <= 19) {
            // Right column: going down back toward Go.
            return new Point(5, index - 15);
        }

        throw new IllegalArgumentException(
                "This layout only supports indices 0-19, got: " + index);
    }

    private void drawCasilla(Graphics g, Casilla casilla, int col, int row) {
        int x = col * CELL_SIZE;
        int y = row * CELL_SIZE;

        if (casilla instanceof CasillaPropiedad) {
            CasillaPropiedad propiedad = (CasillaPropiedad) casilla;
            g.setColor(colorFor(propiedad.getColorGroup()));
            g.fillRect(x, y, CELL_SIZE, CELL_SIZE);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, CELL_SIZE, CELL_SIZE);
            drawCenteredText(g, propiedad.getName(), x, y);
        } else {
            // Event squares (corners and "Take a Card"): text only, no
            // colored fill, just outlined on the gray background.
            g.setColor(Color.BLACK);
            g.drawRect(x, y, CELL_SIZE, CELL_SIZE);
            drawCenteredText(g, casilla.getName(), x, y);
        }
    }

    private Color colorFor(String colorGroup) {
        switch (colorGroup) {
            case "Light Blue":
                return new Color(173, 216, 230);
            case "Pink":
                return Color.PINK;
            case "Orange":
                return Color.ORANGE;
            case "Yellow":
                return Color.YELLOW;
            default:
                return Color.WHITE;
        }
    }

    private void drawCenteredText(Graphics g, String text, int x, int y) {
        g.setColor(Color.BLACK);
        FontMetrics metrics = g.getFontMetrics();
        int textX = x + (CELL_SIZE - metrics.stringWidth(text)) / 2;
        int textY = y + (CELL_SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(text, textX, textY);
    }

    private void drawCarta(Graphics g) {
        if (cartaMostrada == null) return;

        int w = CELL_SIZE * 3, h = CELL_SIZE * 2;
        int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;

        g.setColor(Color.WHITE);
        g.fillRect(x, y, w, h);
        g.setColor(Color.BLACK);
        g.drawRect(x, y, w, h);

        FontMetrics m = g.getFontMetrics();
        String texto = cartaMostrada.getTexto();
        g.drawString(texto, x + (w - m.stringWidth(texto)) / 2, y + h / 2);
    }

    public static void main(String[] args) {
        Board board = BoardFactory.createSampleBoard();
        board.addPlayer(new Player(0, "Perro"));
        board.addPlayer(new Player(1, "Sombrero"));
        Player player2 = new Player(2, "Carro");
        board.addPlayer(player2, 7);


        JFrame frame = new JFrame("Graphic_Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new GraphicTest(board));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        board.getCasilla(0).onLand(player2, null);

    }
}
