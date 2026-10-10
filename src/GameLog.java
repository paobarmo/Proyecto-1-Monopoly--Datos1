import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;


/**
 * Chronological record of what happens in the game, in plain text, meant to
 * be read by the players (e.g. "Turno de Jugador 1, lance dados.").
 *
 * Anything in the game that needs to tell the players something (turn
 * changes, dice results, and in the future every event) writes here with
 * add(). The screen listens for new messages and displays them.
 *
 * Player names can be looked up with nameOf, so events can write messages
 * such as:  log.add(log.nameOf(player) + " recibe 50 dólares.");
 *
 * Thread-safe: messages can be added from any thread. Listeners are called
 * on the thread that adds the message, so a listener that updates the
 * screen must hand the work over to the Swing event thread.
 */
public class GameLog {

    private final List<String> messages = new ArrayList<>();
    private final List<Consumer<String>> listeners = new ArrayList<>();
    private final Map<Player, String> playerNames = new HashMap<>();

    private final List<Consumer<CartaEvento>> cardListeners = new CopyOnWriteArrayList<>();

    private final List<IntConsumer> moveListeners = new CopyOnWriteArrayList<>();

    private final List<Consumer<String>> teleportListeners = new CopyOnWriteArrayList<>();

    /**
     * Associates a display name with a player, so messages can mention them.
     */
    public synchronized void registerPlayer(Player player, String name) {
        Objects.requireNonNull(player, "player cannot be null.");
        Objects.requireNonNull(name, "name cannot be null.");
        playerNames.put(player, name);
    }

    /**
     * Returns the display name registered for the player.
     */
    public synchronized String nameOf(Player player) {
        String name = playerNames.get(player);
        return name != null ? name : String.valueOf(player);
    }

    /**
     * Adds a message to the end of the log and notifies every listener.
     */
    public synchronized void add(String message) {
        Objects.requireNonNull(message, "message cannot be null.");
        messages.add(message);
        for (Consumer<String> listener : listeners) {
            listener.accept(message);
        }
    }

    /**
     * Registers a listener called with every message added from now on.
     */
    public synchronized void addListener(Consumer<String> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null."));
    }

    /** Registra quién quiere mostrar las cartas que se saquen. */
    public void addCardListener(Consumer<CartaEvento> listener) {
        cardListeners.add(Objects.requireNonNull(listener, "listener cannot be null."));
    }

    /** Registra quién atiende los movimientos extra pedidos por cartas/eventos. */
    public void addMoveListener(IntConsumer listener) {
        moveListeners.add(Objects.requireNonNull(listener, "listener cannot be null."));
    }

    /**
     * Pide que el jugador actual avance más casillas cuando termine el
     * movimiento en curso. No mueve nada por sí mismo.
     */
    /**
     * Pide que el jugador actual se mueva más casillas cuando termine el
     * movimiento en curso: positivo avanza, negativo retrocede. No mueve
     * nada por sí mismo.
     */
    public void requestExtraMove(int steps) {
        if (steps == 0) {
            throw new IllegalArgumentException("Steps cannot be zero.");
        }
        for (IntConsumer listener : moveListeners) {
            listener.accept(steps);
        }
    }

    /** Registra quién atiende los pedidos de ir directo a una casilla. */
    public void addTeleportListener(Consumer<String> listener) {
        teleportListeners.add(Objects.requireNonNull(listener, "listener cannot be null."));
    }

    /**
     * Pide que el jugador actual vaya directo a la casilla con ese nombre
     * cuando termine el movimiento en curso. No mueve nada por sí mismo.
     */
    public void requestTeleport(String squareName) {
        Objects.requireNonNull(squareName, "squareName cannot be null.");
        for (Consumer<String> listener : teleportListeners) {
            listener.accept(squareName);
        }
    }

    /**
     * Muestra una carta a los jugadores. Los listeners pueden bloquear hasta
     * que la carta se cierre, así que no llamar desde el hilo de Swing.
     */
    public void showCard(CartaEvento card) {
        Objects.requireNonNull(card, "card cannot be null.");
        for (Consumer<CartaEvento> listener : cardListeners) {
            listener.accept(card);
        }
    }

    /**
     * Returns a read-only copy of every message added so far.
     */
    public synchronized List<String> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }
}
