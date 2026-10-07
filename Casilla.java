/**
 * Abstract base class representing a square (Casilla) on the Monopoly board.
 * Concrete subclasses will specialize this into normal squares (properties,
 * with name, price, owner, rent, etc.) and event squares (Go, Go to Jail,
 * Draw a Card, etc.).
 *
 * NOTE: This class depends on the Player type, which is not yet implemented.
 * A minimal contract is assumed (an object that can own properties and be
 * affected by landing on a square).
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
     * Defines what happens when a player lands on this square.
     * Each subclass must provide its own behavior:
     *   - Normal squares: charge rent, offer purchase, etc.
     *   - Event squares: apply their specific effect (go to jail, draw a
     *     card, collect from Go, etc.).
     *
     * A Bank reference is passed in because it is the only authority
     * allowed to modify player balances; squares must never move money
     * except through it.
     *
     * @param player the player who landed on the square
     * @param bank the bank that holds and manages every player's money
     */
    public abstract void onLand(Player player, Bank bank);

    /**
     * Returns a short description of the square, useful for logging or
     * displaying board state before the graphical part is built.
     *
     * @return a textual description of the square
     */
    public abstract String describe();

    @Override
    public String toString() {
        return name + " (position " + position + ")";
    }
}
