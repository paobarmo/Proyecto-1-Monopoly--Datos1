import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

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

    /**
     * Returns a read-only copy of every message added so far.
     */
    public synchronized List<String> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }
}
