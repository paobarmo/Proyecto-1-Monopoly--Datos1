/**
 * Asks a player whether they want to buy a property. The game logic only
 * knows this interface; the screen provides the real implementation (a
 * pop-up window), so the model does not depend on any user interface.
 */
@FunctionalInterface
public interface PurchasePrompt {

    /**
     * Asks whether the player wants to buy the property. Blocks until the
     * player answers.
     *
     * @param playerName display name of the player who landed on the property
     * @param property the property offered
     * @return true if the player wants to buy it, false if they reject it
     */
    boolean askToBuy(String playerName, CasillaPropiedad property);
}
