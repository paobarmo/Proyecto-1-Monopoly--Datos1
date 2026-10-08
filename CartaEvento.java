public class CartaEvento {

    private final int numero;      // del 1 al 5
    private final String texto;
    private boolean usada;

    public CartaEvento(int numero, String texto) {
        this.numero = numero;
        this.texto = texto;
        this.usada = false;
    }

    public int getNumero() { return numero; }
    public String getTexto() { return texto; }
    public boolean isUsada() { return usada; }

    public void marcarUsada() { this.usada = true; }
    public void reiniciar()   { this.usada = false; }
}