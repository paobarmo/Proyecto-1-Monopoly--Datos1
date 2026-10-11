import java.util.OptionalLong;

/**
 * Abstraction over the RFID hardware. Bank depends only on this interface,
 * so the real reader implementation (serial port, microcontroller, library,
 * etc.) can be written and swapped without touching Bank.
 *
 * IDs are represented as long because card IDs can exceed the range of int
 * (e.g. 2209842189 > Integer.MAX_VALUE).
 */
public interface CardReader {

    /**
     * Waits for a card to be presented and returns its ID.
     * Implementations should block until a card is read or a timeout occurs.
     *
     * @return the card ID, or an empty OptionalLong if no card was read
     *         (timeout, read error, etc.)
     */
    OptionalLong readCardId();
}
