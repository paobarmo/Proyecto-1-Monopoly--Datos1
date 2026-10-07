/**
 * Concrete Casilla representing an event square (e.g. Go, Go to Jail,
 * Draw a Card, Income Tax, etc.). It does not implement any event logic
 * itself; it only identifies which event applies and delegates execution
 * to the event list/manager that will be created later.
 *
 * NOTE: EventManager (or equivalent) is not yet implemented. The call in
 * onLand is left as a placeholder showing the expected integration point.
 */
public class CasillaEvento extends Casilla {

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
    public void onLand(Player player, Bank bank) {
        // Pending integration with the future event list/manager.
        // Expected shape once available, e.g.:
        //   EventManager.getInstance().trigger(eventId, player, bank);
    }

    @Override
    public String describe() {
        return getName() + " [event: " + eventId + "]";
    }
}
