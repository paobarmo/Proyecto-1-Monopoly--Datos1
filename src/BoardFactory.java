/**
 * Builds the game Board with its 24 squares (4 corners + 5 squares per side).
 * The jail itself is NOT part of the list: it is a separate area outside the
 * board, and the "Jail Visit" corner only exists so players can pass by.
 *
 * Board order (clockwise, starting at Go):
 *    0 Go                (corner)
 *    1-5 bottom side     (Light Blue group + "Pay taxes")
 *    6 Jail Visit        (corner)
 *    7-11 left side      (Pink group + "Fortune")
 *   12 Free Parking      (corner)
 *   13-17 top side       (Orange group + "Pay taxes")
 *   18 Go to Jail        (corner)
 *   19-23 right side     (Yellow group + "Fortune")
 *
 * All event squares currently point to events that do nothing yet.
 */
public class BoardFactory {

    private BoardFactory() {
        // Utility class: not meant to be instantiated.
    }

    public static Board createBoard() {
        Board board = new Board();

        addEvent(board, "Go", "GO");
        addProperty(board, "Ravenswood", "Light Blue", 100, 25, 30);
        addProperty(board, "Moonfall", "Light Blue", 100, 25, 30);
        addEvent(board, "Pay taxes", "PAY_TAXES");
        addProperty(board, "WitchHut", "Light Blue", 120, 30, 35);
        addProperty(board, "RavensWood", "Light Blue", 120, 30, 35);

        addEvent(board, "Jail Visit", "JAIL_VISIT");

        addProperty(board, "Pumpkin mansion", "Pink", 150, 50, 70);
        addProperty(board, "Crimsonwoods", "Pink", 150, 50, 70);
        addEvent(board, "Fortune", "FORTUNE");
        addProperty(board, "Bat Caves", "Pink", 170, 70, 90);
        addProperty(board, "Scarecrow avenue", "Pink", 150, 70, 90);

        addEvent(board, "Free Parking", "FREE_PARKING");

        addProperty(board, "Apple Town", "Orange", 200, 100, 130);
        addProperty(board, "Spooky house", "Orange", 200, 100, 130);
        addEvent(board, "Pay taxes", "PAY_TAXES");
        addProperty(board, "Pumpkin Spice", "Orange", 250, 120, 150);
        addProperty(board, "Scary Tree", "Orange", 250, 120, 150);

        addEvent(board, "Go to Jail", "GO_TO_JAIL");

        addProperty(board, "Spirit Chest", "Yellow", 400, 180, 200);
        addProperty(board, "Undead Parade", "Yellow", 400, 180, 200);
        addEvent(board, "Fortune", "FORTUNE");
        addProperty(board, "Candy Street", "Yellow", 400, 200, 230);
        addProperty(board, "Ghostly Church", "Yellow", 400, 200, 230);

        return board;
    }

    // The position of each square is taken from the current board size, so
    // it always matches the square's real index in the list.
    private static void addProperty(Board board, String name, String colorGroup,
                                    int price, int rentWithoutGroup, int rentWithGroup) {
        CasillaPropiedad property = new CasillaPropiedad(name, board.getSize(), price, colorGroup,
                rentWithoutGroup, rentWithGroup);
        property.setBoard(board); // lets the property check whether its owner holds the full group
        board.addCasilla(property);
    }

    private static void addEvent(Board board, String name, String eventId) {
        CasillaEvento event = new CasillaEvento(name, board.getSize(), eventId);
        event.setBoard(board); // lets events that act on the board (e.g. Go to Jail) use it
        board.addCasilla(event);
    }
}
