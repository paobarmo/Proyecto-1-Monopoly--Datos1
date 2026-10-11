/** Error de regla del juego (turno, saldo, propiedad ocupada, etc.). El mensaje se envía al cliente. */
public class ErrorJuego extends Exception {
    public ErrorJuego(String mensaje) {
        super(mensaje);
    }
}
