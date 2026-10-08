/**
 * Concrete Casilla representing an event square (e.g. Go, Go to Jail,
 * Draw a Card, Income Tax, etc.). It does not implement any event logic
 * itself; it only identifies which event applies and delegates execution
 * to the event list/manager that will be created later.
 *
 * NOTE: EventManager (or equivalent) is not yet implemented. The call in
 * onLand is left as a placeholder showing the expected integration point.
 */
import java.util.function.Consumer;

public class CasillaEvento extends Casilla {
    private final String eventId;
    private static MazoEventos mazo = new MazoEventos();
    private static Consumer<CartaEvento> alMostrarCarta;   // lo configura la UI

    public static void setAlMostrarCarta(Consumer<CartaEvento> listener) {
        alMostrarCarta = listener;
    }

    public CasillaEvento(String name, int position, String eventId) { //constructor
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
        CartaEvento carta = mazo.sacar();
        if (alMostrarCarta != null) alMostrarCarta.accept(carta);
        // Pending integration with the future event list/manager.
        // Expected shape once available, e.g.:
        //   EventManager.getInstance().trigger(eventId, player, bank);
    }

    @Override
    public String describe() {
        return getName() + " [event: " + eventId + "]";
    }
}
