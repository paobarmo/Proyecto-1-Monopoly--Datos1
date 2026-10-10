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

    // Va directo a Spooky House, sin pasar por Go ni por las casillas intermedias.
    private static final GameEvent IR_A_SPOOKY_HOUSE = (player, bank, log) -> {
        log.add(log.nameOf(player) + " va directo a Spooky House.");
        log.requestTeleport("Spooky house");
    };

    private static final int MONTO_PAGO = 50;

    // Paga 50 al banco. Si no tiene fondos, no paga (igual que Pay taxes).
    private static final GameEvent PAGAR_50 = (player, bank, log) -> {
        String name = log.nameOf(player);

        if (bank.getBalance(player) < MONTO_PAGO) {
            log.add(name + " debe pagar " + MONTO_PAGO + " pero no tiene fondos suficientes.");
            return;
        }

        log.add(name + " paga " + MONTO_PAGO + " al banco.");
        log.add("Confirme su transacción.");

        EventManager.runWithConfirmation(log, () -> bank.removeMoney(player, MONTO_PAGO));
    };

    private static final int MONTO_COBRO = 100;

    // Cobra 100 del banco.
    private static final GameEvent COBRAR_100 = (player, bank, log) -> {
        log.add(log.nameOf(player) + " recibe " + MONTO_COBRO + " del banco.");
        log.add("Confirme su transacción.");

        EventManager.runWithConfirmation(log, () -> bank.addMoney(player, MONTO_COBRO));
    };

    private static final List<CartaEvento> MAZO = List.of(
            new CartaEvento(1, "Avanza 3 casillas.", AVANZAR_3),
            new CartaEvento(2, "Retrocede 3 casillas. Muajaja", RETROCEDER_3),
            new CartaEvento(3, "Ve directamente a Spooky House.", IR_A_SPOOKY_HOUSE),
            new CartaEvento(4, "Donas sangre a un vampiro, y 50 al banco.", PAGAR_50),
            new CartaEvento(5, "Ganaste un concurso de disfraces, recibes 100.", COBRAR_100));


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