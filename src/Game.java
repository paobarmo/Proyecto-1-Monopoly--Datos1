import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Holds the state of one game: the Board, the Bank, the players, the game
 * log, whose turn it is and which players have already lost. It connects
 * the model classes so the screens only need a Game.
 *
 * A player loses when they cannot pay (or would be left with 0). Players who
 * lost are skipped in the turns and their properties become available
 * again. The game is over when only one player is left.
 */
public class Game {

    private static final int STARTING_BALANCE = 1500;

    /** Time each player has to take their turn, in seconds. */
    public static final int TURN_TIME_SECONDS = 120;

    // RFID card IDs for players 1 to 4 (each player always uses the same card).
    // NOTE: the IDs of players 3 and 4 (123 and 321) are TEST values; replace
    // them with the real card IDs when available.
    private static final long[] CARD_IDS = {57482001L, 2209842189L, 123L, 321L};

    private final Board board;
    private final Bank bank;
    private final GameLog log = new GameLog();
    private final List<Player> players;
    private final List<String> playerNames;

    // Players who lost. Changed by a background thread, read by the screen.
    private final Set<Player> eliminatedPlayers = ConcurrentHashMap.newKeySet();

    private volatile PurchasePrompt purchasePrompt;
    private volatile int currentPlayerIndex = 0;

    public Game(int playerCount, CardReader cardReader) {
        if (playerCount < 1 || playerCount > CARD_IDS.length) {
            throw new IllegalArgumentException(
                    "Number of players must be between 1 and " + CARD_IDS.length + ".");
        }

        this.board = BoardFactory.createBoard();

        List<Player> createdPlayers = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Map<Player, Long> cardIds = new LinkedHashMap<>();

        for (int i = 0; i < playerCount; i++) {
            String name = "Jugador " + (i + 1);
            Player player = new Player(i + 1, name);

            createdPlayers.add(player);
            names.add(name);
            cardIds.put(player, CARD_IDS[i]);
            log.registerPlayer(player, name);
            board.addPlayer(player); // every player starts on Go
        }

        this.bank = new Bank(cardIds, STARTING_BALANCE, cardReader);
        this.players = Collections.unmodifiableList(createdPlayers);
        this.playerNames = Collections.unmodifiableList(names);

        announceTurn();
    }

    public Board getBoard() {
        return board;
    }

    public Bank getBank() {
        return bank;
    }

    public GameLog getLog() {
        return log;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public Player getPlayer(int index) {
        return players.get(index);
    }

    public String getPlayerName(int index) {
        return playerNames.get(index);
    }

    /**
     * Returns the index of the player in the player list, or -1 if the
     * player does not belong to this game.
     */
    public int indexOf(Player player) {
        return players.indexOf(player);
    }

    /**
     * Returns the board index of the square the given player is standing on,
     * or -1 if the player already lost (they are no longer on the board).
     */
    public int getPlayerPosition(int playerIndex) {
        if (isEliminated(playerIndex)) {
            return -1;
        }
        return board.getCurrentCasilla(players.get(playerIndex)).getPosition();
    }

    // ---------------------------------------------------------------- turns

    /**
     * Returns the index (0-based) of the player whose turn it is.
     */
    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    /**
     * Ends the current turn and gives the turn to the next player who has
     * not lost, going back to the first player after the last one. The new
     * turn is announced in the log.
     */
    public void endTurn() {
        int next = currentPlayerIndex;
        do {
            next = (next + 1) % players.size();
        } while (isEliminated(next) && getActivePlayerCount() > 0);

        currentPlayerIndex = next;
        announceTurn();
    }

    private void announceTurn() {
        log.add("Turno de " + playerNames.get(currentPlayerIndex) + ", lance dados.");
    }

    // ------------------------------------------------------------- movement

    /**
     * Moves the current player the given number of squares (normally the
     * dice total) and writes the dice result to the log.
     *
     * The player's position on the Board changes right away, but NO square
     * events are triggered here, so the screen can show the move square by
     * square. The caller must then call triggerPassed for each square in
     * MoveResult.getPassedSquares() (as the player goes through it), and
     * triggerLanded for the landing square, and finally endTurn().
     *
     * @param steps number of squares to advance (must be positive)
     * @return the squares passed through and the square landed on
     */
    public MoveResult moveCurrentPlayer(int steps) {
        MoveResult result = board.movePlayer(getCurrentPlayer(), steps);
        log.add("¡Los dados han dado un " + steps + "!");
        return result;
    }

    /**
     * Triggers the "passed through" reaction of a square for the current
     * player (e.g. Go).
     *
     * May block while waiting for the player's card or for a pop-up answer,
     * so it must not be called from the Swing event thread.
     */
    public void triggerPassed(Casilla square) {
        square.onPass(getCurrentPlayer(), this);
    }

    /**
     * Announces that the current player landed on a square and triggers its
     * reaction.
     *
     * May block while waiting for the player's card or for a pop-up answer,
     * so it must not be called from the Swing event thread.
     */
    public void triggerLanded(Casilla square) {
        log.add(playerNames.get(currentPlayerIndex) + " cae en " + square.getName() + ".");
        square.onLand(getCurrentPlayer(), this);
    }

    // ------------------------------------------------------ player decisions

    /**
     * Sets the object that asks players whether they want to buy a property
     * (the screen provides it).
     */
    public void setPurchasePrompt(PurchasePrompt purchasePrompt) {
        this.purchasePrompt = purchasePrompt;
    }

    /**
     * Asks the player whether they want to buy the property. Blocks until
     * they answer. Returns false if no prompt was set.
     */
    public boolean askToBuy(Player player, CasillaPropiedad property) {
        PurchasePrompt prompt = purchasePrompt;
        return prompt != null && prompt.askToBuy(log.nameOf(player), property);
    }

    /**
     * Runs a bank transaction, which asks for the player's card. While the
     * card does not match, the player is told so and asked again: there is
     * no time limit and no maximum number of attempts.
     *
     * Blocks while waiting for the card, so it must not be called from the
     * Swing event thread.
     */
    public void confirmTransaction(BooleanSupplier transaction) {
        while (!transaction.getAsBoolean()) {
            log.add("Tarjeta no válida. Confirme su transacción.");
        }
        log.add("Transacción confirmada.");
    }

    // ------------------------------------------------- losing and winning

    public boolean isEliminated(int playerIndex) {
        return eliminatedPlayers.contains(players.get(playerIndex));
    }

    /**
     * Returns how many players have not lost yet.
     */
    public int getActivePlayerCount() {
        return players.size() - eliminatedPlayers.size();
    }

    /**
     * The game is over when one player or fewer remain.
     */
    public boolean isGameOver() {
        return getActivePlayerCount() <= 1;
    }

    /**
     * Makes a player lose: they are removed from the board, skipped in the
     * turns, and their properties become available again.
     */
    public void eliminatePlayer(Player player) {
        if (!eliminatedPlayers.add(player)) {
            return; // already eliminated
        }

        log.add("El " + log.lowerNameOf(player) + " ha perdido.");
        board.removePlayer(player);

        int released = 0;
        for (int i = 0; i < board.getSize(); i++) {
            Casilla square = board.getCasilla(i);
            if (square instanceof CasillaPropiedad) {
                CasillaPropiedad property = (CasillaPropiedad) square;
                if (player.equals(property.getOwner())) {
                    property.setOwner(null);
                    released++;
                }
            }
        }

        if (released > 0) {
            log.add("Sus propiedades vuelven a estar disponibles.");
        }
    }

    /**
     * Returns the index of the winner: the only player left, or, if several
     * are still in the game (it was ended early), the one with the most
     * money among them. Returns -1 if there is a tie for the most money.
     */
    public int getWinnerIndex() {
        int best = -1;
        int bestBalance = Integer.MIN_VALUE;
        boolean tie = false;

        for (int i = 0; i < players.size(); i++) {
            if (isEliminated(i)) {
                continue;
            }

            int balance = bank.getBalance(players.get(i));
            if (balance > bestBalance) {
                best = i;
                bestBalance = balance;
                tie = false;
            } else if (balance == bestBalance) {
                tie = true;
            }
        }

        return tie ? -1 : best;
    }
}
