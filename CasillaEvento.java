/**
 * Concrete Casilla representing an event square (e.g. Go, Go to Jail,
 * Fortune, Pay taxes, etc.). It does not implement any event logic itself;
 * it only identifies which event applies and delegates execution to
 * EventManager.
 *
 * The Go event is special: it triggers both when a player lands on the
 * square and when a player passes through it without stopping.
 *
 * Triggering an event may block while waiting for the player's card (RFID
 * confirmation), so onLand and onPass must not be called from the Swing
 * event thread.
 */
public class CasillaEvento extends Casilla {

    private static final String GO_EVENT_ID = "GO";

    private final String eventId;

    public CasillaEvento(String name, int position, String eventId) {
        super(name, position);

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Event id cannot be null or blank.");
        }

        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

    @Override
    public void onLand(Player player, Bank bank, GameLog log) {
        EventManager.trigger(eventId, player, bank, log);
    }

    @Override
    public void onPass(Player player, Bank bank, GameLog log) {
        // Only the Go event also reacts to being passed without stopping.
        if (GO_EVENT_ID.equals(eventId)) {
            EventManager.trigger(eventId, player, bank, log);
        }
    }

    @Override
    public String describe() {
        return getName() + " [event: " + eventId + "]";
    }
}
