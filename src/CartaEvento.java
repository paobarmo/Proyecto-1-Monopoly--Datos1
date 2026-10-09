import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Carta de Fortuna. Cada carta tiene un número, un texto y una acción que
 * se ejecuta cuando el jugador la ha leído. Por ahora hay 5 cartas con
 * textos provisionales y acciones sin efecto: se definirán después.
 *
 * La acción puede mover dinero (siempre a través del Bank) y escribir en el
 * GameLog. Puede bloquearse esperando la tarjeta RFID, así que no debe
 * ejecutarse en el hilo de Swing.
 */
public final class CartaEvento {

    // Acción por defecto: no hace nada (pendiente de definir).
    // Avanza 3 casillas: pide el movimiento; GamePanel lo anima y dispara las casillas.
    private static final GameEvent AVANZAR_3 = (player, bank, log) -> {
        log.add(log.nameOf(player) + " avanza 3 casillas.");
        log.requestExtraMove(3);
    };

    private static final GameEvent RETROCEDER_3 = (player, bank, log) -> {
        log.add(log.nameOf(player) + " retrocede 3 casillas.");
        log.requestExtraMove(-3);
    };

    private static final List<CartaEvento> MAZO = List.of(
            new CartaEvento(1, "Avanza 3 casillas.", AVANZAR_3),
            new CartaEvento(2, "Retrocede 3 casillas.", RETROCEDER_3));


    private final int numero;
    private final String texto;
    private final GameEvent accion;

    private CartaEvento(int numero, String texto, GameEvent accion) {
        this.numero = numero;
        this.texto = Objects.requireNonNull(texto, "texto cannot be null.");
        this.accion = Objects.requireNonNull(accion, "accion cannot be null.");
    }

    /** Devuelve una carta al azar del mazo (puede repetirse). */
    public static CartaEvento sortear() {
        return MAZO.get(ThreadLocalRandom.current().nextInt(MAZO.size()));
    }

    public int getNumero() {
        return numero;
    }

    public String getTexto() {
        return texto;
    }

    /** Ejecuta la acción de la carta para el jugador. */
    public void aplicar(Player player, Bank bank, GameLog log) {
        accion.execute(player, bank, log);
    }
}