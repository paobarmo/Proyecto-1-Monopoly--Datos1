import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the state of one game: the Board, the Bank, the players, the game
 * log and whose turn it is. It connects the model classes so the screens
 * only need a Game.
 *
 * Implemented so far: turn order, player movement (including the squares
 * passed through) and the game log. Purchases, rent decisions, jail, etc.
 * are not implemented yet.
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

    private int currentPlayerIndex = 0;

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
     * Returns the board index of the square the given player is standing on.
     */
    public int getPlayerPosition(int playerIndex) {
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
     * Ends the current turn and gives the turn to the next player, going
     * back to the first player after the last one. The new turn is announced
     * in the log.
     */
    public void endTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
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
     * May block while waiting for the player's card (RFID confirmation), so
     * it must not be called from the Swing event thread.
     */
    public void triggerPassed(Casilla square) {
        square.onPass(getCurrentPlayer(), bank, log);
    }

    /**
     * Announces that the current player landed on a square and triggers its
     * reaction.
     *
     * May block while waiting for the player's card (RFID confirmation), so
     * it must not be called from the Swing event thread.
     */
    public void triggerLanded(Casilla square) {
        log.add(playerNames.get(currentPlayerIndex) + " cae en " + square.getName() + ".");
        square.onLand(getCurrentPlayer(), bank, log);
    }
}
