import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Usa todas las validaciones RFID y de saldo del Bank ORIGINAL.
 * Solo registra una transaccion cuando super.* devuelve true.
 */
public final class BancoConHistorial extends Bank {
    private final HistorialTransacciones historial = new HistorialTransacciones();
    private final Map<Player, String> nombres = new IdentityHashMap<>();
    private int turnoActual = 1;

    /** Permite anotar tipo y descripcion especificos al implementar compras/rentas. */
    private static final class Concepto {
        final String tipo;
        final String descripcion;
        Concepto(String tipo, String descripcion) {
            this.tipo = tipo;
            this.descripcion = descripcion;
        }
    }
    private final ThreadLocal<Concepto> conceptoDelHilo = new ThreadLocal<>();

    public BancoConHistorial(Map<Player, Long> tarjetas, int saldoInicial, CardReader lector) {
        super(tarjetas, saldoInicial, lector);
        int i = 1;
        // Game utiliza LinkedHashMap: su orden es Jugador 1, 2, 3, 4.
        for (Player jugador : tarjetas.keySet()) nombres.put(jugador, "Jugador " + i++);
    }

    public HistorialTransacciones getHistorial() { return historial; }

    public synchronized void registrarNombre(Player jugador, String nombre) {
        if (jugador != null && nombre != null && !nombre.isBlank()) nombres.put(jugador, nombre);
    }

    public synchronized void siguienteTurno() { turnoActual++; }
    public synchronized int getTurnoActual() { return turnoActual; }

    private String nombre(Player jugador) {
        return nombres.getOrDefault(jugador, "Jugador sin identificar");
    }

    private void anotar(String tipoGenerico, String origen, String destino,
                        int monto, String descripcionGenerica) {
        Concepto contexto = conceptoDelHilo.get();
        String tipo = contexto == null ? tipoGenerico : contexto.tipo;
        String descripcion = contexto == null ? descripcionGenerica : contexto.descripcion;
        historial.registrar(turnoActual, tipo, origen, destino, monto, descripcion);
    }

    @Override
    public synchronized boolean addMoney(Player jugador, int monto) {
        boolean autorizado = super.addMoney(jugador, monto);
        if (autorizado) anotar("INGRESO_DEL_BANCO", "BANCO", nombre(jugador), monto,
                "Abono autorizado con tarjeta RFID");
        return autorizado;
    }

    @Override
    public synchronized boolean removeMoney(Player jugador, int monto) {
        boolean autorizado = super.removeMoney(jugador, monto);
        if (autorizado) anotar("PAGO_AL_BANCO", nombre(jugador), "BANCO", monto,
                "Cargo autorizado con tarjeta RFID");
        return autorizado;
    }

    @Override
    public synchronized boolean transfer(Player origen, Player destino, int monto) {
        boolean autorizado = super.transfer(origen, destino, monto);
        if (autorizado) anotar("PAGO_ENTRE_JUGADORES", nombre(origen), nombre(destino), monto,
                "Transferencia autorizada con tarjeta RFID");
        return autorizado;
    }

    /**
     * Opcional para las clases de casilla, cuando se implemente el concepto real:
     * banco.conConcepto("PAGO_ALQUILER", "Alquiler de ...", () -> banco.transfer(a,b,100));
     * La confirmacion RFID sigue siendo obligatoria y solo se registra si tiene exito.
     */
    public boolean conConcepto(String tipo, String descripcion, BooleanSupplier operacion) {
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(descripcion);
        Objects.requireNonNull(operacion);
        Concepto anterior = conceptoDelHilo.get();
        conceptoDelHilo.set(new Concepto(tipo, descripcion));
        try {
            return operacion.getAsBoolean();
        } finally {
            if (anterior == null) conceptoDelHilo.remove();
            else conceptoDelHilo.set(anterior);
        }
    }
}
