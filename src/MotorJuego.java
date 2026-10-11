import java.util.List;
import java.util.function.Consumer;

/**
 * Lo que el servidor necesita del juego. MotorJuegoReal lo implementa sobre Game/Bank/Board.
 * Todas las llamadas las hace el servidor dentro de un mismo lock.
 */
public interface MotorJuego {
    class Lanzamiento {
        public int d1, d2, desde, hasta;
        public String casilla = "";
    }

    class Resolucion {
        public boolean eliminado;
        public boolean puedeComprar;
        public int precio;
    }

    int cantidad();

    int turnoActual();

    boolean activo(int i);

    int saldo(int i);

    int posicion(int i);

    int patrimonio(int i);

    void registrarNombre(int i, String nombre);

    /** Recibe cada mensaje que el juego agrega al GameLog. */
    void alLog(Consumer<String> oyente);

    /** Lanza los dos dados y mueve al jugador en turno. */
    Lanzamiento lanzar();

    /** Ejecuta las casillas recorridas y la de llegada (alquiler, eventos, inicio...). */
    Resolucion resolver();

    String comprar() throws ErrorJuego;

    /**
     * Dueños de las propiedades como "posicion:indice,posicion:indice" (solo las que
     * ya tienen dueño; cadena vacía si ninguna). El servidor lo envía en cada ESTADO.
     */
    default String propietarios() {
        return "";
    }

    /** Turnos que al jugador i le faltan por perder en la cárcel (0 si está libre). */
    default int turnosCarcel(int i) {
        return 0;
    }

    void terminarTurno();

    void eliminar(int i);

    List<String> historial();
}
