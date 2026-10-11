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

    private static String lowerFirst(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
