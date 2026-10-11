/**
 * An event that can be triggered by an event square (Go, Pay taxes, etc.).
 * Implementations change the game through the Bank and tell the players
 * what happened through the GameLog.
 */
@FunctionalInterface
public interface GameEvent {

    /**
     * Executes the event for the given player.
     *
     * May block while waiting for the player's card (RFID confirmation), so
     * it must not run on the Swing event thread.
     */
    void execute(Player player, Bank bank, GameLog log);
}
