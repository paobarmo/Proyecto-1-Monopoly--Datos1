import java.util.HashMap;
import java.util.Map;

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
 * Money-related events ask the player to confirm with their RFID card (see
 * Game.confirmTransaction): the Bank only executes the transaction if the
 * card read matches the player.
 */
public final class EventManager {

    private static final int GO_REWARD = 200;
    private static final int TAX_AMOUNT = 200;

    private static final Map<String, GameEvent> EVENTS = new HashMap<>();

    static {
        EVENTS.put("GO", EventManager::goEvent);
        EVENTS.put("PAY_TAXES", EventManager::payTaxesEvent);
    }

    private EventManager() {
        // Utility class: not meant to be instantiated.
    }

    /**
     * Triggers the event with the given id for the player. May block while
     * waiting for the player's card, so do not call it from the Swing event
     * thread.
     */
    public static void trigger(String eventId, Player player, Game game) {
        GameEvent event = EVENTS.get(eventId);

        if (event == null) {
            GameLog log = game.getLog();
            log.add(log.nameOf(player) + " activa el evento " + eventId + " (aún sin efecto).");
            return;
        }

        event.execute(player, game);
    }

    // ---------------------------------------------------------------- events

    // The player collects money from the bank.
    private static void goEvent(Player player, Game game) {
        GameLog log = game.getLog();
        Bank bank = game.getBank();

        log.add("Go! El " + log.lowerNameOf(player) + " gana " + GO_REWARD + ".");
        log.add("Confirme su transacción.");

        game.confirmTransaction(() -> bank.addMoney(player, GO_REWARD));
    }

    // The player pays money to the bank. Not having enough means losing.
    private static void payTaxesEvent(Player player, Game game) {
        GameLog log = game.getLog();
        Bank bank = game.getBank();
        String name = log.nameOf(player);

        if (bank.getBalance(player) <= TAX_AMOUNT) {
            log.add(name + " debe pagar " + TAX_AMOUNT + " pero no tiene dinero suficiente.");
            game.eliminatePlayer(player);
            return;
        }

        log.add(name + " paga " + TAX_AMOUNT + ".");
        log.add("Confirme su transacción.");

        game.confirmTransaction(() -> bank.removeMoney(player, TAX_AMOUNT));
    }
}
