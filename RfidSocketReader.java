import java.util.OptionalLong;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * CardReader para el servidor en red. Reemplaza a ConsoleCardReader.
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
    private volatile IntConsumer alEsperar = i -> { };
    private volatile boolean esperandoTarjeta;

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
        if (esperandoTarjeta) cola.offer(uid);
    }

    public void limpiar() {
        cola.clear();
    }

    @Override
    public OptionalLong readCardId() {
        int i = jugadorActual;
        if (tarjetasSimuladas != null) {
            return (i >= 0 && i < tarjetasSimuladas.length)
                    ? OptionalLong.of(tarjetasSimuladas[i]) : OptionalLong.empty();
        }
        cola.clear();
        esperandoTarjeta = true;
        alEsperar.accept(i);
        try {
            Long uid = cola.poll(esperaMs, TimeUnit.MILLISECONDS);
            return uid == null ? OptionalLong.empty() : OptionalLong.of(uid);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OptionalLong.empty();
        } finally {
            esperandoTarjeta = false;
            cola.clear();
        }
    }
}
