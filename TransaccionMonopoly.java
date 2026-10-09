import java.time.LocalDateTime;
import java.util.Objects;

/** Un movimiento economico confirmado por Bank, no un mensaje de consola. */
public final class TransaccionMonopoly {
    private final long numero;
    private final LocalDateTime fechaHora;
    private final int turno;
    private final String tipo;
    private final String origen;
    private final String destino;
    private final int monto;
    private final String descripcion;

    public TransaccionMonopoly(long numero, LocalDateTime fechaHora, int turno,
                               String tipo, String origen, String destino,
                               int monto, String descripcion) {
        if (numero < 1 || turno < 1 || monto <= 0) {
            throw new IllegalArgumentException("Numero, turno y monto deben ser positivos");
        }
        this.numero = numero;
        this.fechaHora = Objects.requireNonNull(fechaHora);
        this.turno = turno;
        this.tipo = Objects.requireNonNull(tipo);
        this.origen = Objects.requireNonNull(origen);
        this.destino = Objects.requireNonNull(destino);
        this.monto = monto;
        this.descripcion = Objects.requireNonNull(descripcion);
    }

    public long getNumero() { return numero; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public int getTurno() { return turno; }
    public String getTipo() { return tipo; }
    public String getOrigen() { return origen; }
    public String getDestino() { return destino; }
    public int getMonto() { return monto; }
    public String getDescripcion() { return descripcion; }
}
