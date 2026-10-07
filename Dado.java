import java.util.Random;

public class Dado {
    private int[] array = {1, 2, 3, 4, 5, 6};

    public int lanzar() {
        Random random = new Random();
        int indice = random.nextInt(array.length);
        return array[indice]; //numero
    }
}
//Remplazar con DadoSerial