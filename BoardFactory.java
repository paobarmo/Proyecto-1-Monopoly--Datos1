/**
 * Builds a small sample Board for testing purposes, before any graphical
 * interface exists. Layout: 4 corner event squares (Go, Jail, Free Parking,
 * Go To Jail) plus 4 non-corner squares per side (16 total), for a 20-square
 * board. A single extra
 * "Take a Card" event square replaces one property slot on the second side.
 *
 * All names, prices and rents are placeholders.
 *
 * Board order (clockwise, starting at Go):
 *   0  Go                (corner, event)
 *   1  A  - Light Blue   (property)
 *   2  B  - Light Blue   (property)
 *   3  C  - Light Blue   (property)
 *   4  D  - Light Blue   (property)
 *   5  Jail               (corner, event)
 *   6  E  - Pink         (property)
 *   7  Take a Card        (event)
 *   8  F  - Pink         (property)
 *   9  G  - Pink         (property)
 *   10 Free Parking       (corner, event)
 *   11 H  - Orange       (property)
 *   12 I  - Orange       (property)
 *   13 J  - Orange       (property)
 *   14 K  - Orange       (property)
 *   15 Go To Jail         (corner, event)
 *   16 L  - Yellow       (property)
 *   17 M  - Yellow       (property)
 *   18 N  - Yellow       (property)
 *   19 O  - Yellow       (property)
 */
public class BoardFactory {

    private static final int PLACEHOLDER_PRICE = 5;
    private static final int PLACEHOLDER_RENT_WITHOUT_GROUP = 1;
    private static final int PLACEHOLDER_RENT_WITH_GROUP = 2;

    private BoardFactory() {
        // Utility class: not meant to be instantiated.
    }

    /**
     * Creates the sample 20-square board described above.
     *
     * @return a fully populated Board
     */
    public static Board createSampleBoard() {
        Board board = new Board();

        board.addCasilla(new CasillaEvento("Go", 0, "GO"));

        board.addCasilla(property("A", 1, "Light Blue"));
        board.addCasilla(property("B", 2, "Light Blue"));
        board.addCasilla(property("C", 3, "Light Blue"));
        board.addCasilla(property("D", 4, "Light Blue"));

        board.addCasilla(new CasillaEvento("Jail", 5, "JAIL"));

        board.addCasilla(property("E", 6, "Pink"));
        board.addCasilla(new CasillaEvento("Take a Card", 7, "DRAW_CARD"));
        board.addCasilla(property("F", 8, "Pink"));
        board.addCasilla(property("G", 9, "Pink"));

        board.addCasilla(new CasillaEvento("Free Parking", 10, "FREE_PARKING"));

        board.addCasilla(property("H", 11, "Orange"));
        board.addCasilla(property("I", 12, "Orange"));
        board.addCasilla(property("J", 13, "Orange"));
        board.addCasilla(property("K", 14, "Orange"));

        board.addCasilla(new CasillaEvento("Go To Jail", 15, "GO_TO_JAIL"));

        board.addCasilla(property("L", 16, "Yellow"));
        board.addCasilla(property("M", 17, "Yellow"));
        board.addCasilla(property("N", 18, "Yellow"));
        board.addCasilla(property("O", 19, "Yellow"));

        return board;
    }

    private static CasillaPropiedad property(String name, int position, String colorGroup) {
        return new CasillaPropiedad(name, position, PLACEHOLDER_PRICE, colorGroup,
                PLACEHOLDER_RENT_WITHOUT_GROUP, PLACEHOLDER_RENT_WITH_GROUP);
    }
}
