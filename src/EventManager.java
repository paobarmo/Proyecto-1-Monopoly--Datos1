import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * List of the events that event squares can trigger, identified by the
 * event id of the square (see BoardFactory). Squares call trigger(); this
 * class finds the matching event and runs it.
 *
 * To add an event: write a private static method with the signature of
 * GameEvent.execute and register it in the static block below. Event ids
 * with no registered event just write a line to the log saying they have no
 * effect yet.
 *
 * Money-related events ask the player to confirm with their RFID card: the
 * Bank only executes the transaction if the card read matches the player.
 */
public final class EventManager {

    private static final int GO_REWARD = 200;
    private static final int TAX_AMOUNT = 200;
    private static final int JAIL_TURNS = 3;

    // Fortune cards (see MazoEventos / CartaEvento).
    private static final String FORTUNE_EVENT_ID = "FORTUNE";   // must match the id in BoardFactory
    private static final MazoEventos FORTUNE_DECK = new MazoEventos();
    private static final int FORTUNE_REWARD = 100;
    private static final int FORTUNE_FINE = 50;
    private static final String CANDY_STREET = "Candy Street";   // must match the name in BoardFactory

    /** An event that also needs the board (e.g. to move a player somewhere). */
    @FunctionalInterface
    private interface BoardEvent {
        void execute(Player player, Bank bank, GameLog log, Board board);
    }

    private static final Map<String, GameEvent> EVENTS = new HashMap<>();
    private static final Map<String, BoardEvent> BOARD_EVENTS = new HashMap<>();

    // Players who could not pay a mandatory event (e.g. taxes); Game eliminates them.
    private static final Set<Player> UNABLE_TO_PAY = ConcurrentHashMap.newKeySet();

    /** True (only once) if the player could not pay an event. Used by Game. */
    static boolean consumeUnableToPay(Player player) {
        return UNABLE_TO_PAY.remove(player);
    }

    static {
        EVENTS.put("GO", EventManager::goEvent);
        EVENTS.put("PAY_TAXES", EventManager::payTaxesEvent);
        BOARD_EVENTS.put("GO_TO_JAIL", EventManager::goToJailEvent);
        BOARD_EVENTS.put(FORTUNE_EVENT_ID, EventManager::fortuneEvent);
        // Jail Visit had an error message because it was not registered, si I made it as nothing here.
        EVENTS.put("JAIL_VISIT", (player, bank, log) -> { });
    }

    private EventManager() {
        // Utility class: not meant to be instantiated.
    }

    /**
     * Triggers the event with the given id for the player. May block while
     * waiting for the player's card, so do not call it from the Swing event
     * thread.
     */
    public static void trigger(String eventId, Player player, Bank bank, GameLog log) {
        GameEvent event = EVENTS.get(eventId);

        if (event == null) {
            log.add(log.nameOf(player) + " activa el evento " + eventId + " (aún sin efecto).");
            return;
        }

        event.execute(player, bank, log);
    }

    /**
     * Same as trigger above, but also gives the event access to the board
     * (needed by events such as Go to Jail). Events that do not need the
     * board run exactly as before.
     */
    public static void trigger(String eventId, Player player, Bank bank, GameLog log, Board board) {
        BoardEvent boardEvent = BOARD_EVENTS.get(eventId);

        if (boardEvent != null && board != null) {
            boardEvent.execute(player, bank, log, board);
            return;
        }

        trigger(eventId, player, bank, log);
    }

    // ---------------------------------------------------------------- events

    // The player collects money from the bank.
    private static void goEvent(Player player, Bank bank, GameLog log) {
        log.add("Go! El " + lowerFirst(log.nameOf(player)) + " gana " + GO_REWARD + ".");
        log.add("Confirme su transacción.");

        runWithConfirmation(log, () -> bank.addMoney(player, GO_REWARD));
    }

    // The player pays money to the bank.
    private static void payTaxesEvent(Player player, Bank bank, GameLog log) {
        String name = log.nameOf(player);

        if (bank.getBalance(player) < TAX_AMOUNT) {
            log.add(name + " debe pagar " + TAX_AMOUNT + " pero no tiene fondos suficientes.");
            UNABLE_TO_PAY.add(player);
            return;
        }

        log.add(name + " paga " + TAX_AMOUNT + ".");
        log.add("Confirme su transacción.");

        runWithConfirmation(log, () -> bank.removeMoney(player, TAX_AMOUNT));
    }

    // The player goes to jail and loses their next turns. No money is moved,
    // so no card confirmation is needed.
    private static void goToJailEvent(Player player, Bank bank, GameLog log, Board board) {
        log.add(log.nameOf(player) + " va a la cárcel y pierde los próximos "
                + JAIL_TURNS + " turnos.");
        board.sendToJail(player, JAIL_TURNS);
    }

    // The player draws a Fortune card and its effect is applied.
    private static void fortuneEvent(Player player, Bank bank, GameLog log, Board board) {
        CartaEvento carta = FORTUNE_DECK.sacar();
        String name = log.nameOf(player);

        log.add(name + " saca una carta de Fortuna.");
        log.add(carta.getTexto());

        switch (carta.getNumero()) {
            case 1: // Avanza 3 espacios
                moveAndResolve(player, bank, log, board, 3, true);
                break;
            case 2: // Retrocede 3 espacios
                // Board only moves forward: going back 3 = advancing (size - 3).
                // The squares "passed" are ignored so Go is not collected by mistake.
                moveAndResolve(player, bank, log, board, board.getSize() - 3, false);
                break;
            case 3: // Error bancario: recibes 100
                log.add("Confirme su transacción.");
                runWithConfirmation(log, () -> bank.addMoney(player, FORTUNE_REWARD));
                break;
            case 4: // La matrícula: paga 50
                if (bank.getBalance(player) < FORTUNE_FINE) {
                    log.add(name + " debe pagar " + FORTUNE_FINE + " pero no tiene fondos suficientes.");
                    UNABLE_TO_PAY.add(player);   // Game eliminates them, same as with taxes
                    return;
                }
                log.add(name + " paga " + FORTUNE_FINE + ".");
                log.add("Confirme su transacción.");
                runWithConfirmation(log, () -> bank.removeMoney(player, FORTUNE_FINE));
                break;
            case 5: // Ve a Candy Street
                goToSquare(player, bank, log, board, CANDY_STREET);
                break;
            default:
                break;
        }
    }

    // --------------------------------------------------------------- helpers

    /**
     * Runs a bank transaction, which asks for the player's card. While the
     * card does not match, the player is told so and asked again: there is
     * no time limit and no maximum number of attempts.
     */
    private static void runWithConfirmation(GameLog log, BooleanSupplier transaction) {
        while (!transaction.getAsBoolean()) {
            log.add("Tarjeta no válida. Confirme su transacción.");
        }
        log.add("Transacción confirmada.");
    }

    // Moves the player and applies the squares' reactions, like a normal move.
    private static void moveAndResolve(Player player, Bank bank, GameLog log, Board board,
                                       int steps, boolean triggerPassed) {
        MoveResult result = board.movePlayer(player, steps);

        if (triggerPassed) {
            for (Casilla passed : result.getPassedSquares()) {
                passed.onPass(player, bank, log);
            }
        }

        Casilla landing = result.getLandingSquare();
        log.add(log.nameOf(player) + " cae en " + landing.getName() + ".");
        landing.onLand(player, bank, log);
    }

    // Moves the player forward to the square with the given name.
    private static void goToSquare(Player player, Bank bank, GameLog log, Board board,
                                   String squareName) {
        int target = -1;
        for (int i = 0; i < board.getSize(); i++) {
            if (board.getCasilla(i).getName().equalsIgnoreCase(squareName)) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            log.add("No se encontró la casilla " + squareName + ".");
            return;
        }

        int current = board.getCurrentCasilla(player).getPosition();
        int steps = (target - current + board.getSize()) % board.getSize();
        if (steps == 0) {
            return;   // already there
        }
        moveAndResolve(player, bank, log, board, steps, true);   // collects Go if it passes it
    }

    private static String lowerFirst(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
