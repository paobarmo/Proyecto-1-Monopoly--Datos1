/**
 * Protocolo de texto sobre TCP: una línea por mensaje, campos separados por '|', codificación UTF-8.
 *
 * CLIENTE -> SERVIDOR
 *   CONECTAR|nombre            primer mensaje de un jugador
 *   TIRAR_DADOS
 *   COMPRAR_PROPIEDAD
 *   NO_COMPRAR
 *   TERMINAR_TURNO
 *   CONSULTAR_ESTADO
 *   CONSULTAR_TRANSACCIONES
 *
 * DISPOSITIVO (módulo RFID/dado) -> SERVIDOR
 *   DISPOSITIVO                primer mensaje del módulo electrónico
 *   RFID|uid                   uid de la tarjeta (número decimal)
 *
 * SERVIDOR -> CLIENTES
 *   OK|detalle                          la acción fue aceptada
 *   ERROR|motivo                        la acción fue rechazada
 *   BIENVENIDO|indice|nombre
 *   LOBBY|conectados|total|n1,n2,...
 *   INICIO|n1,n2,...
 *   TURNO|indice|nombre|numeroTurno
 *   DADOS|indice|d1|d2|desde|hasta|casilla
 *   PUEDE_COMPRAR|casilla|precio        solo al jugador en turno
 *   COMPRA|indice|texto
 *   ESTADO|turno|i,nombre,saldo,posicion,activo;...
 *   LOG|texto                           mensajes del GameLog (alquileres, eventos, etc.)
 *   ESPERANDO_TARJETA|indice|segundos
 *   EVENTO|texto
 *   ELIMINADO|indice|nombre
 *   TRANSACCIONES|n  /  TX|i|texto  /  FIN_TRANSACCIONES
 *   FIN|indiceGanador|nombre|motivo|patrimonio
 */
public final class Protocolo {
    public static final String CONECTAR = "CONECTAR";
    public static final String DISPOSITIVO = "DISPOSITIVO";
    public static final String RFID = "RFID";
    public static final String TIRAR_DADOS = "TIRAR_DADOS";
    public static final String COMPRAR_PROPIEDAD = "COMPRAR_PROPIEDAD";
    public static final String NO_COMPRAR = "NO_COMPRAR";
    public static final String TERMINAR_TURNO = "TERMINAR_TURNO";
    public static final String CONSULTAR_ESTADO = "CONSULTAR_ESTADO";
    public static final String CONSULTAR_TRANSACCIONES = "CONSULTAR_TRANSACCIONES";

    private Protocolo() {
    }

    /** Quita los caracteres que usa el protocolo como separadores y los saltos de línea. */
    public static String limpiar(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('|', '/').replace(';', ' ').replace(',', ' ')
                .replace('\r', ' ').replace('\n', ' ').trim();
    }

    /** Igual que limpiar pero conserva ',' ';' y '|' (para el último campo de un mensaje). */
    public static String soloUnaLinea(String s) {
        return s == null ? "" : s.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
