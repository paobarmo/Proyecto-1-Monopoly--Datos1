import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Historial con LISTA DOBLEMENTE ENLAZADA PROPIA.
 * No emplea List/LinkedList/Queue de Java como estructura de almacenamiento.
 * Permite recorrer en ambos sentidos, consultar por jugador/tipo y exportar TXT.
 */
public final class HistorialTransacciones {
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final class Nodo {
        final TransaccionMonopoly transaccion;
        Nodo anterior;
        Nodo siguiente;
        Nodo(TransaccionMonopoly transaccion) { this.transaccion = transaccion; }
    }

    private Nodo primero;
    private Nodo ultimo;
    private int cantidad;
    private long siguienteNumero = 1;

    /** Solo se llama despues de una operacion economica AUTORIZADA. */
    public synchronized TransaccionMonopoly registrar(int turno, String tipo,
            String origen, String destino, int monto, String descripcion) {
        TransaccionMonopoly transaccion = new TransaccionMonopoly(
                siguienteNumero++, LocalDateTime.now(), turno,
                tipo, origen, destino, monto, descripcion);
        Nodo nodo = new Nodo(transaccion);
        if (ultimo == null) {
            primero = nodo;
        } else {
            ultimo.siguiente = nodo;
            nodo.anterior = ultimo;
        }
        ultimo = nodo;
        cantidad++;
        return transaccion;
    }

    public synchronized int cantidad() { return cantidad; }

    public synchronized void desdeMasAntigua(Consumer<TransaccionMonopoly> accion) {
        Objects.requireNonNull(accion);
        for (Nodo n = primero; n != null; n = n.siguiente) accion.accept(n.transaccion);
    }

    public synchronized void desdeMasReciente(Consumer<TransaccionMonopoly> accion) {
        Objects.requireNonNull(accion);
        for (Nodo n = ultimo; n != null; n = n.anterior) accion.accept(n.transaccion);
    }

    public synchronized void buscarPorJugador(String nombre, Consumer<TransaccionMonopoly> accion) {
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(accion);
        for (Nodo n = primero; n != null; n = n.siguiente) {
            TransaccionMonopoly t = n.transaccion;
            if (nombre.equalsIgnoreCase(t.getOrigen()) || nombre.equalsIgnoreCase(t.getDestino()))
                accion.accept(t);
        }
    }

    public synchronized void buscarPorTipo(String tipo, Consumer<TransaccionMonopoly> accion) {
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(accion);
        for (Nodo n = primero; n != null; n = n.siguiente) {
            if (tipo.equalsIgnoreCase(n.transaccion.getTipo())) accion.accept(n.transaccion);
        }
    }

    /** Crea/sobrescribe un .txt UTF-8 con TODOS los campos que exige el PDF. */
    public void exportarTXT(Path destino) throws IOException {
        Objects.requireNonNull(destino, "Destino del TXT requerido");
        StringBuilder reporte = new StringBuilder();
        synchronized (this) {
            reporte.append("MONOPOLY - HISTORIAL DE TRANSACCIONES\n")
                    .append("Fecha de exportacion: ").append(LocalDateTime.now().format(FECHA)).append('\n')
                    .append("Total de transacciones: ").append(cantidad).append("\n\n");
            if (cantidad == 0) {
                reporte.append("Todavia no hay transacciones economicas registradas.\n");
            }
            for (Nodo n = primero; n != null; n = n.siguiente) {
                TransaccionMonopoly t = n.transaccion;
                reporte.append("------------------------------------------\n")
                        .append("Transaccion #: ").append(t.getNumero()).append('\n')
                        .append("Fecha y hora : ").append(t.getFechaHora().format(FECHA)).append('\n')
                        .append("Turno        : ").append(t.getTurno()).append('\n')
                        .append("Tipo         : ").append(limpiar(t.getTipo())).append('\n')
                        .append("Origen       : ").append(limpiar(t.getOrigen())).append('\n')
                        .append("Destino      : ").append(limpiar(t.getDestino())).append('\n')
                        .append("Monto        : ").append(t.getMonto()).append('\n')
                        .append("Descripcion  : ").append(limpiar(t.getDescripcion())).append("\n\n");
            }
        }
        Files.writeString(destino, reporte.toString(), StandardCharsets.UTF_8);
    }

    private static String limpiar(String valor) {
        return valor.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }
}
