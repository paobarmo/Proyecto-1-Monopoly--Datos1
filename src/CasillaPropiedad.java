/**
 * Concrete Casilla representing a purchasable property square (e.g. streets
 * in classic Monopoly). Holds price, color group, rent, and current owner.
 *
 * This implementation does not support houses: rent depends only on whether
 * the owner holds every property of the same color group or not.
 *
 * NOTE: Player is implemented separately; it is used here only as an
 * identity (map key, equality check). All money movement goes through
 * Bank, which is the sole authority over player balances.
 */
public class CasillaPropiedad extends Casilla {

    private final int price;
    private final String colorGroup;
    private final int rentWithoutGroup;
    private final int rentWithGroup;

    private Player owner;

    // Used to ask whether the owner holds the whole color group (see onLand).
    private Board board;

    public CasillaPropiedad(String name, int position, int price, String colorGroup,
                             int rentWithoutGroup, int rentWithGroup) {
        super(name, position);

        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        if (colorGroup == null || colorGroup.isBlank()) {
            throw new IllegalArgumentException("Color group cannot be null or blank.");
        }
        if (rentWithoutGroup < 0 || rentWithGroup < 0) {
            throw new IllegalArgumentException("Rent values cannot be negative.");
        }

        this.price = price;
        this.colorGroup = colorGroup;
        this.rentWithoutGroup = rentWithoutGroup;
        this.rentWithGroup = rentWithGroup;
        this.owner = null;
    }

    public int getPrice() {
        return price;
    }

    public String getColorGroup() {
        return colorGroup;
    }

    public int getRentWithoutGroup() {
        return rentWithoutGroup;
    }

    public int getRentWithGroup() {
        return rentWithGroup;
    }

    public Player getOwner() {
        return owner;
    }

    public boolean isOwned() {
        return owner != null;
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    /**
     * Gives the property access to the board it belongs to, so it can check
     * whether its owner holds every property of the color group.
     */
    public void setBoard(Board board) {
        this.board = board;
    }

    /**
     * Returns the rent currently owed for this square.
     *
     * @param ownerHasFullGroup whether the owner holds every property of
     *                          this color group
     * @return the applicable rent amount
     */
    public int getRent(boolean ownerHasFullGroup) {
        return ownerHasFullGroup ? rentWithGroup : rentWithoutGroup;
    }

    @Override
    public void onLand(Player player, Bank bank, GameLog log) {
        if (!isOwned()) {
            // No owner yet: landing here costs nothing. The purchase is offered
            // right after by the screen (local game) or the server (network
            // game), through Game.buyCurrentProperty().
            return;
        }

        if (owner.equals(player)) {
            // Landing on your own property has no effect.
            return;
        }

        // The owner charges the higher rent only if they hold every property
        // of this color group.
        boolean ownerHasFullGroup = board != null && board.ownsFullGroup(owner, colorGroup);

        int rentDue = getRent(ownerHasFullGroup);
        if (bank.transfer(player, owner, rentDue)) {
            log.add(log.nameOf(player) + " paga " + rentDue + " de renta a "
                    + log.nameOf(owner) + " por " + getName()
                    + (ownerHasFullGroup ? " (grupo " + colorGroup + " completo)" : "") + ".");
        } else {
            log.add("No se pudo verificar la tarjeta de " + log.nameOf(player)
                    + "; no se cobró la renta de " + getName() + ".");
        }
    }

    @Override
    public String describe() {
        String ownerName = isOwned() ? owner.toString() : "none";
        return getName() + " [" + colorGroup + "] price=" + price
                + ", rent(no group)=" + rentWithoutGroup
                + ", rent(with group)=" + rentWithGroup
                + ", owner=" + ownerName;
    }
}
