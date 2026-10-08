/**
 * Console-only check that the board assembles correctly. Prints every
 * square in board order using Casilla.describe().
 */
public class BoardDemo {

    public static void main(String[] args) {
        Board board = BoardFactory.createBoard();

        System.out.println("Board size: " + board.getSize());

        for (int i = 0; i < board.getSize(); i++) {
            Casilla casilla = board.getCasilla(i);
            System.out.println(i + ": " + casilla.describe());
        }
    }
}
