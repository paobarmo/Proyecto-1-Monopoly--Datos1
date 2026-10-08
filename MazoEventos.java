import java.util.*;

public class MazoEventos {
    private final List<CartaEvento> cartas = new ArrayList<>();
    private final Random random = new Random();

    public MazoEventos() {
        cartas.add(new CartaEvento(1, "Carta 1: Avanza 3 espacios."));
        cartas.add(new CartaEvento(2, "Carta 2: Retrocede 3 espacios."));
        cartas.add(new CartaEvento(3, "Carta 3: Error bancario, Recibes 100."));
        cartas.add(new CartaEvento(4, "Carta 4: ¡La matrícula! Paga 50."));
        cartas.add(new CartaEvento(5, "Carta 5: Ve a Candy Street"));
    }

    public CartaEvento sacar() {
        List<CartaEvento> disponibles = new ArrayList<>();
        for (CartaEvento c : cartas) {
            if (!c.isUsada()) disponibles.add(c);
        }
        if (disponibles.isEmpty()) {          // se acabaron: se reinicia el mazo
            cartas.forEach(CartaEvento::reiniciar);
            disponibles.addAll(cartas);
        }
        CartaEvento elegida = disponibles.get(random.nextInt(disponibles.size()));
        elegida.marcarUsada();
        return elegida;
    }
}