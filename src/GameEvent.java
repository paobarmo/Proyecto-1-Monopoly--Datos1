/**
 * An event that can be triggered by an event square (Go, Pay taxes, etc.).
 * Implementations change the game through the Game (its Bank for money, its
 * GameLog to tell the players what happened).
 */
@FunctionalInterface
public interface GameEvent {

    /**
     * Executes the event for the given player.
     *
     * May block while waiting for the player's card (RFID confirmation), so
     * it must not run on the Swing event thread.
     */
    void execute(Player player, Game game);
}
