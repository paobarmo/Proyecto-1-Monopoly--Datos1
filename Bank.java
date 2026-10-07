import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds and manages the money of every player in the game.
 * No other class can read or modify the
 * player's money except through the methods exposed here.
 *
 * Encapsulation notes:
 *   - The internal balance map is private and final; it is never returned
 *     directly, so external code cannot mutate it.
 *   - All changes to a balance go through addMoney/removeMoney/transfer,
 *     which validate their inputs before applying any change.
 *   - There is no public no-argument constructor and no method to replace
 *     the whole balance map, so a Bank's state can only evolve one
 *     validated operation at a time.
 */
public class Bank {

    private static final int MIN_PLAYERS = 1;
    private static final int MAX_PLAYERS = 4;

    private final Map<Player, Integer> balances;

    /**
     * Creates a bank and registers every player with the same starting
     * balance.
     *
     * @param players list of 1 to 4 players
     * @param startingBalance initial amount of money for every player
     */
    public Bank(List<Player> players, int startingBalance) {
        if (players == null) {
            throw new IllegalArgumentException("Player list cannot be null.");
        }
        if (players.size() < MIN_PLAYERS || players.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "Number of players must be between " + MIN_PLAYERS + " and " + MAX_PLAYERS + ".");
        }
        if (startingBalance < 0) {
            throw new IllegalArgumentException("Starting balance cannot be negative.");
        }

        this.balances = new HashMap<>();
        for (Player player : players) {
            if (player == null) {
                throw new IllegalArgumentException("Player cannot be null.");
            }
            if (balances.containsKey(player)) {
                throw new IllegalArgumentException("Duplicate player in player list.");
            }
            balances.put(player, startingBalance);
        }
    }

    /**
     * Adds money to a player's balance.
     *
     * @param player the player receiving money
     * @param amount amount to add (must be positive)
     */
    public void addMoney(Player player, int amount) {
        checkRegistered(player);
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount to add must be positive.");
        }

        balances.put(player, balances.get(player) + amount);
    }

    /**
     * Removes money from a player's balance.
     *
     * @param player the player paying money
     * @param amount amount to remove (must be positive, and no greater than
     *               the player's current balance)
     */
    public void removeMoney(Player player, int amount) {
        checkRegistered(player);
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount to remove must be positive.");
        }

        int current = balances.get(player);
        if (current < amount) {
            throw new IllegalStateException(player + " has insufficient funds.");
        }

        balances.put(player, current - amount);
    }

    /**
     * Moves money directly from one player to another (e.g. rent payments).
     * Equivalent to removeMoney(from, amount) followed by addMoney(to,
     * amount), applied as a single validated operation.
     *
     * @param from player paying
     * @param to player receiving
     * @param amount amount to transfer (must be positive)
     */
    public void transfer(Player from, Player to, int amount) {
        checkRegistered(from);
        checkRegistered(to);
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount to transfer must be positive.");
        }

        removeMoney(from, amount);
        addMoney(to, amount);
    }

    /**
     * Returns a player's current balance.
     *
     * @param player the player to query
     * @return current amount of money
     */
    public int getBalance(Player player) {
        checkRegistered(player);
        return balances.get(player);
    }

    private void checkRegistered(Player player) {
        if (player == null || !balances.containsKey(player)) {
            throw new IllegalArgumentException("Player is not registered in this bank.");
        }
    }
}
