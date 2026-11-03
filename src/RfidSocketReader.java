import java.util.OptionalLong;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * CardReader para el servidor en red. Puede usar UID reales o simulados.
 *
 * Modo hardware: el módulo electrónico envía "RFID|uid" al servidor y esa tarjeta se entrega a
 * Bank.verify(). Si no llega ninguna en esperaMs, la operación se rechaza (readCardId vacío).
 *
 * Modo simulado (sin hardware): devuelve automáticamente la tarjeta del jugador en turno,
 * para poder probar el juego completo sin el lector físico.
 */
public class RfidSocketReader implements CardReader {
    private final long[] tarjetasSimuladas;
    private final long esperaMs;
    private final BlockingQueue<Long> cola = new LinkedBlockingQueue<>();
    private volatile int jugadorActual = -1;
    private volatile boolean esperandoRFID = false;
    // Marcador reservado para despertar poll() al solicitar fin de partida.
    private static final long UID_CANCELACION = Long.MIN_VALUE;
    private volatile boolean cancelarSolicitado = false;
    private volatile IntConsumer alEsperar = i -> { };

    /** @param tarjetasSimuladas ids por índice de jugador, o null para usar el lector físico */
    public RfidSocketReader(long[] tarjetasSimuladas, long esperaMs) {
        this.tarjetasSimuladas = tarjetasSimuladas;
        this.esperaMs = esperaMs;
    }

    public void setJugadorActual(int indice) {
        this.jugadorActual = indice;
    }

    /** Se invoca justo antes de bloquear esperando la tarjeta (el servidor avisa a los clientes). */
    public void setAlEsperar(IntConsumer alEsperar) {
        this.alEsperar = alEsperar;
    }

    public long getEsperaSegundos() {
        return esperaMs / 1000;
    }

    /** Llamado por el hilo que atiende al módulo electrónico. No toma el lock del juego. */
    public void presentar(long uid) {
        // Una tarjeta pasada antes de una compra no debe autorizar otra futura.
        if (tarjetasSimuladas == null && esperandoRFID) cola.offer(uid);
    }

    public void limpiar() {
        cola.clear();
    }

    /** Interrumpe una espera RFID sin registrar ninguna tarjeta ficticia.
     * Se invoca cuando un jugador solicita terminar la partida.
     */
    public void cancelarEspera() {
        cancelarSolicitado = true;
        cola.clear();
        cola.offer(UID_CANCELACION);
    }

    @Override
    public OptionalLong readCardId() {
        // Si ya se pidio terminar, ningun pago pendiente se puede autorizar.
        if (cancelarSolicitado) return OptionalLong.empty();
        int i = jugadorActual;
        if (tarjetasSimuladas != null) {
            return (i >= 0 && i < tarjetasSimuladas.length)
                    ? OptionalLong.of(tarjetasSimuladas[i]) : OptionalLong.empty();
        }
        cola.clear();
        esperandoRFID = true;
        try {
            alEsperar.accept(i);
            if (cancelarSolicitado) return OptionalLong.empty();
            Long uid = cola.poll(esperaMs, TimeUnit.MILLISECONDS);
            if (cancelarSolicitado || uid == null || uid == UID_CANCELACION)
                return OptionalLong.empty();
            return OptionalLong.of(uid);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OptionalLong.empty();
        } finally {
            esperandoRFID = false;
            cola.clear();
        }
    }
}
