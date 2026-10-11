import java.util.Random;

/**
 * Six-sided die. Created so DiceTestButton compiles and works while the
 * teammate's final dice implementation is not available.
 *
 * Replace this file with the teammate's Dado once it is delivered, as long
 * as it keeps a no-argument constructor and an int lanzar() method that
 * returns a value from 1 to 6.
 */
public class Dado {

    private static final int FACES = 6;

    private final Random random = new Random();

    /**
     * Rolls the die.
     *
     * @return a random number from 1 to 6 (both included)
     */
    public int lanzar() {
        return random.nextInt(FACES) + 1;
    }
}
