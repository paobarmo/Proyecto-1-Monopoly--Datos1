import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds and manages the money of every player in the game. The bank is the
 * single source of truth for balances: no other class can read or modify a
 * player's money except through the methods exposed here.
 *
 * Every operation that changes a balance requires RFID verification: the
 * bank asks the CardReader for a card, and the operation is executed only
 * if the card ID read matches the ID registered for the player who must
 * authorize it.
 *
 * Encapsulation notes:
 *   - Balances and registered card IDs are private and final; they are
 *     never returned directly, so external code cannot mutate them.
 *   - The constructor copies the data it receives, so later changes to the
 *     caller's map cannot affect the bank.
 *   - All changes go through addMoney/removeMoney/transfer, which validate
 *     their inputs and verify the card before applying any change.
 *
 * NOTE: Player is used only as a map key (identity).
 */
public class Bank {

    private static final int MIN_PLAYERS = 1;
    private static final int MAX_PLAYERS = 4;

    private final Map<Player, Integer> balances;
    private final Map<Player, Long> registeredCardIds;
    private final CardReader cardReader;

    /**
     * Creates a bank, registering every player with their card ID and the
     * same starting balance.
     *
     * @param cardIdsByPlayer map of 1 to 4 players to their RFID card IDs
     *                        (IDs must be unique)
     * @param startingBalance initial amount of money for every player
     * @param cardReader the RFID reader used to verify transactions
     */
    public Bank(Map<Player, Long> cardIdsByPlayer, int startingBalance, CardReader cardReader) {
        if (cardIdsByPlayer == null) {
            throw new IllegalArgumentException("Player map cannot be null.");
        }
        if (cardIdsByPlayer.size() < MIN_PLAYERS || cardIdsByPlayer.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "Number of players must be between " + MIN_PLAYERS + " and " + MAX_PLAYERS + ".");
        }
        if (startingBalance < 0) {
            throw new IllegalArgumentException("Starting balance cannot be negative.");
        }
        if (cardReader == null) {
            throw new IllegalArgumentException("Card reader cannot be null.");
        }

        // Balances are read by the screen while a background thread (waiting for
        // the player's card) may be updating them, so the map must be thread-safe.
        this.balances = new ConcurrentHashMap<>();
        this.registeredCardIds = new HashMap<>();
        this.cardReader = cardReader;

        Set<Long> usedIds = new HashSet<>();
        for (Map.Entry<Player, Long> entry : cardIdsByPlayer.entrySet()) {
            Player player = entry.getKey();
            Long cardId = entry.getValue();

            if (player == null || cardId == null) {
                throw new IllegalArgumentException("Player and card ID cannot be null.");
            }
            if (!usedIds.add(cardId)) {
                throw new IllegalArgumentException("Duplicate card ID: " + cardId);
            }

            balances.put(player, startingBalance);
            registeredCardIds.put(player, cardId);
        }
    }

    /**
     * Adds money to a player's balance. The receiving player must verify
     * with their card.
     *
     * @return true if verified and executed, false if verification failed
     */
    public boolean addMoney(Player player, int amount) {
        checkRegistered(player);
        checkPositive(amount, "add");

        if (!verify(player)) {
            return false;
        }

        credit(player, amount);
        return true;
    }

    /**
     * Removes money from a player's balance. The paying player must verify
     * with their card.
     *
     * @return true if verified and executed, false if verification failed
     */
    public boolean removeMoney(Player player, int amount) {
        checkRegistered(player);
        checkPositive(amount, "remove");
        checkFunds(player, amount);

        if (!verify(player)) {
            return false;
        }

        debit(player, amount);
        return true;
    }

    /**
     * Moves money from one player to another (e.g. rent). Only the paying
     * player verifies with their card; a single verification covers the
     * whole transfer.
     *
     * @return true if verified and executed, false if verification failed
     */
    public boolean transfer(Player from, Player to, int amount) {
        checkRegistered(from);
        checkRegistered(to);
        checkPositive(amount, "transfer");
        checkFunds(from, amount);

        if (!verify(from)) {
            return false;
        }

        debit(from, amount);
        credit(to, amount);
        return true;
    }

    /**
     * Returns a player's current balance. Read-only, so it does not require
     * card verification (this is what the display will use).
     */
    public int getBalance(Player player) {
        checkRegistered(player);
        return balances.get(player);
    }

    /**
     * Asks the reader for a card and checks that its ID matches the one
     * registered for the given player.
     */
    private boolean verify(Player player) {
        OptionalLong cardId = cardReader.readCardId();
        return cardId.isPresent() && cardId.getAsLong() == registeredCardIds.get(player);
    }

    private void credit(Player player, int amount) {
        balances.put(player, balances.get(player) + amount);
    }

    private void debit(Player player, int amount) {
        balances.put(player, balances.get(player) - amount);
    }

    private void checkRegistered(Player player) {
        if (player == null || !balances.containsKey(player)) {
            throw new IllegalArgumentException("Player is not registered in this bank.");
        }
    }

    private void checkPositive(int amount, String operation) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount to " + operation + " must be positive.");
        }
    }

    private void checkFunds(Player player, int amount) {
        if (balances.get(player) < amount) {
            throw new IllegalStateException(player + " has insufficient funds.");
        }
    }
}
