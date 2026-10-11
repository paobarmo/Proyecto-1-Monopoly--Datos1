import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the state of one game: the Board, the Bank, the players, the game
 * log and whose turn it is. It connects the model classes so the screens
 * only need a Game.
 *
 * Implemented so far: turn order, player movement (including the squares
 * passed through), the game log, property purchases and rent (higher when
 * the owner holds the whole color group) and the jail (a player sent to jail
 * loses their next three turns).
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

    // Indexes of the players who lost. Concurrent: it is written from the background
    // thread that resolves a move and read by the screen.
    private final Set<Integer> eliminated = ConcurrentHashMap.newKeySet();

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
     * back to the first player after the last one. A player in jail loses
     * their turn: they are skipped (and one jail turn is used up) until the
     * turns run out. The new turn is announced in the log.
     */
    public void endTurn() {
        if (isGameOver()) {
            return;
        }
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        } while (isEliminated(currentPlayerIndex) || skipJailTurn(currentPlayerIndex));
        announceTurn();
    }

    /**
     * If the player is in jail, uses up one of their jail turns and tells the
     * players. Returns true when the turn must be skipped.
     */
    private boolean skipJailTurn(int playerIndex) {
        Player player = players.get(playerIndex);
        if (!board.serveJailTurn(player)) {
            return false;
        }

        String name = playerNames.get(playerIndex);
        int left = board.getJailTurnsLeft(player);
        if (left > 0) {
            log.add(name + " pierde el turno: sigue en la cárcel (le quedan " + left
                    + (left == 1 ? " turno)." : " turnos)."));
        } else {
            log.add(name + " pierde su último turno en la cárcel y queda libre en Jail Visit.");
        }
        return true;
    }

    /**
     * Returns whether the player with the given index is in jail.
     */
    public boolean isInJail(int playerIndex) {
        return board.isInJail(players.get(playerIndex));
    }

    /**
     * Returns how many turns the player with the given index still has to
     * lose in jail (0 if free).
     */
    public int getJailTurnsLeft(int playerIndex) {
        return board.getJailTurnsLeft(players.get(playerIndex));
    }

    /**
     * Frees a player from jail right away (e.g. when they are eliminated).
     */
    public void releaseFromJail(Player player) {
        board.releaseFromJail(player);
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
        int index = currentPlayerIndex;
        log.add(playerNames.get(index) + " cae en " + square.getName() + ".");

        // Cannot pay the rent: loses right away (no mortgages, no trading).
        if (cannotPayRent(square)) {
            eliminatePlayer(index, "no puede pagar la renta de " + square.getName()
                    + " y queda eliminado");
            return;
        }

        square.onLand(getCurrentPlayer(), bank, log);

        // Could not pay a tax.
        if (EventManager.consumeUnableToPay(getCurrentPlayer())) {
            eliminatePlayer(index, "no puede pagar los impuestos y queda eliminado");
        }
    }

    private boolean cannotPayRent(Casilla square) {
        if (!(square instanceof CasillaPropiedad)) {
            return false;
        }
        CasillaPropiedad property = (CasillaPropiedad) square;
        Player owner = property.getOwner();
        Player current = getCurrentPlayer();
        if (owner == null || owner.equals(current)) {
            return false;
        }
        int rent = property.getRent(board.ownsFullGroup(owner, property.getColorGroup()));
        return rent > 0 && bank.getBalance(current) < rent;
    }

    // --------------------------------------------------------- eliminations

    public boolean isEliminated(int playerIndex) {
        return eliminated.contains(playerIndex);
    }

    public int getActivePlayerCount() {
        return players.size() - eliminated.size();
    }

    /** The game is over once someone was eliminated and one player (or none) is left. */
    public boolean isGameOver() {
        return !eliminated.isEmpty() && getActivePlayerCount() <= 1;
    }

    /** Index of the only player left, or -1 if there is none. */
    public int getWinnerIndex() {
        for (int i = 0; i < players.size(); i++) {
            if (!isEliminated(i)) {
                return i;
            }
        }
        return -1;
    }

    /** Active players with the most money (several if tied). Used to end the game early. */
    public List<Integer> getRichestPlayerIndexes() {
        List<Integer> richest = new ArrayList<>();
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < players.size(); i++) {
            if (isEliminated(i)) {
                continue;
            }
            int balance = bank.getBalance(players.get(i));
            if (balance > best) {
                best = balance;
                richest.clear();
                richest.add(i);
            } else if (balance == best) {
                richest.add(i);
            }
        }
        return richest;
    }

    /** Eliminates every active player whose balance is 0. Returns the eliminated indexes. */
    public List<Integer> eliminateBankruptPlayers() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (!isEliminated(i) && bank.getBalance(players.get(i)) <= 0) {
                eliminatePlayer(i, "se quedó sin dinero y queda eliminado");
                result.add(i);
            }
        }
        return result;
    }

    public void eliminatePlayer(int playerIndex) {
        eliminatePlayer(playerIndex, "sale de la partida y queda eliminado");
    }

    /** Takes the player out of the game: leaves the jail and their properties become free. */
    public void eliminatePlayer(int playerIndex, String reason) {
        if (!eliminated.add(playerIndex)) {
            return;
        }
        Player player = players.get(playerIndex);
        releaseFromJail(player);

        int freed = 0;
        for (int i = 0; i < board.getSize(); i++) {
            Casilla square = board.getCasilla(i);
            if (square instanceof CasillaPropiedad
                    && player.equals(((CasillaPropiedad) square).getOwner())) {
                ((CasillaPropiedad) square).setOwner(null);
                freed++;
            }
        }
        log.add(playerNames.get(playerIndex) + " " + reason + "."
                + (freed > 0 ? " Sus propiedades (" + freed + ") quedan libres." : ""));
    }

    // ----------------------------------------------------------- properties

    /**
     * Returns the property the current player is standing on if it has no
     * owner yet (so it could be bought), or null otherwise.
     */
    public CasillaPropiedad getBuyableProperty() {
        Casilla square = board.getCurrentCasilla(getCurrentPlayer());
        if (square instanceof CasillaPropiedad && !((CasillaPropiedad) square).isOwned()) {
            return (CasillaPropiedad) square;
        }
        return null;
    }

    /**
     * Buys the property the current player is standing on: charges its price
     * through the Bank (the player must confirm with their card) and records
     * the player as its owner.
     *
     * May block while waiting for the player's card, so it must not be
     * called from the Swing event thread.
     *
     * @return the message written to the log
     * @throws ErrorJuego if the square cannot be bought, the player cannot
     *                    pay, or the card payment was not confirmed
     */
    public String buyCurrentProperty() throws ErrorJuego {
        Player player = getCurrentPlayer();
        Casilla square = board.getCurrentCasilla(player);

        if (!(square instanceof CasillaPropiedad)) {
            throw new ErrorJuego("Esta casilla no se puede comprar");
        }
        CasillaPropiedad property = (CasillaPropiedad) square;
        if (property.isOwned()) {
            throw new ErrorJuego("La propiedad ya tiene propietario");
        }

        int price = property.getPrice();
        if (bank.getBalance(player) < price) {
            throw new ErrorJuego("Saldo insuficiente: cuesta " + price
                    + " y tienes " + bank.getBalance(player));
        }
        if (price > 0 && !bank.removeMoney(player, price)) {
            throw new ErrorJuego("Pago no confirmado: tarjeta RFID no válida o no presentada");
        }

        property.setOwner(player);

        String message = log.nameOf(player) + " compró " + property.getName() + " por " + price;
        if (board.ownsFullGroup(player, property.getColorGroup())) {
            message += " y completó el grupo " + property.getColorGroup();
        }
        log.add(message);
        return message;
    }

    /**
     * Returns the index (0-based) of the given player, or -1 if the player
     * (or null) is not in this game.
     */
    public int getPlayerIndex(Player player) {
        return players.indexOf(player);
    }

    /**
     * Returns, for every square of the board, the index of the player who
     * owns it, or -1 if it has no owner (or is not a property).
     */
    public int[] getPropertyOwnerIndexes() {
        int[] owners = new int[board.getSize()];
        for (int i = 0; i < owners.length; i++) {
            Casilla square = board.getCasilla(i);
            owners[i] = square instanceof CasillaPropiedad
                    ? getPlayerIndex(((CasillaPropiedad) square).getOwner())
                    : -1;
        }
        return owners;
    }
}
