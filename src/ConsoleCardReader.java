import java.util.OptionalLong;
import java.util.Scanner;

/**
 * TEMPORARY TEST IMPLEMENTATION.
 *
 * Simulates the RFID reader by asking for the card ID on the console, so
 * Bank can be tested without hardware. Replace with the real CardReader
 * implementation once the hardware interface is connected.
 */
public class ConsoleCardReader implements CardReader {

    private final Scanner scanner = new Scanner(System.in);

    @Override
    public OptionalLong readCardId() {
        System.out.print("Present card (type ID): ");
        String input = scanner.nextLine().trim();

        try {
            return OptionalLong.of(Long.parseLong(input));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
