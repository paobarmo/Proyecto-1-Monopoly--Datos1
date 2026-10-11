/**
 * Abstract base class representing a square (Casilla) on the Monopoly board.
 * Concrete subclasses specialize this into normal squares (properties, with
 * name, price, owner, rent, etc.) and event squares (Go, Go to Jail, Draw a
 * Card, etc.).
 *
 * Squares never modify balances directly: money moves only through the
 * Bank. Anything the players should be told is written to the GameLog.
 */
public abstract class Casilla {

    private final String name;
    private final int position;

    protected Casilla(String name, int position) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank.");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative.");
        }

        this.name = name;
        this.position = position;
    }

    /**
     * Returns the display name of the square.
     *
     * @return the square's name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the fixed position of the square on the board.
     *
     * @return the square's position index
     */
    public int getPosition() {
        return position;
    }

    /**
     * Defines what happens when a player lands on this square (stops on it
     * at the end of a move).
     *
     * @param player the player who landed on the square
     * @param bank the bank that holds and manages every player's money
     * @param log the game log, to tell the players what happened
     */
    public abstract void onLand(Player player, Bank bank, GameLog log);

    /**
     * Called when a player passes through this square during a move without
     * stopping on it (it is not called for the square the player lands on;
     * onLand is called for that one). By default nothing happens; squares
     * that react to being passed (such as Go) override it.
     *
     * @param player the player who passed through the square
     * @param bank the bank that holds and manages every player's money
     * @param log the game log, to tell the players what happened
     */
    public void onPass(Player player, Bank bank, GameLog log) {
        // Default: passing through a square has no effect.
    }

    /**
     * Returns a short description of the square, useful for debugging.
     *
     * @return a textual description of the square
     */
    public abstract String describe();

    @Override
    public String toString() {
        return name + " (position " + position + ")";
    }
}
