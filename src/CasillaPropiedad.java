/**
 * Concrete Casilla representing a purchasable property square (e.g. streets
 * in classic Monopoly). Holds price, color group, rent, and current owner.
 *
 * There are no houses: rent depends only on whether the owner holds every
 * property of the same color group or not.
 *
 * When a player lands on it:
 *   - Without an owner: the player is offered to buy it (there are no
 *     auctions). Buying needs the player's card confirmation.
 *   - With another player as owner: the player pays the rent (the
 *     "group" rent if the owner holds the whole color group). If they
 *     cannot pay it, they lose the game.
 *   - Owned by the player themself: nothing happens.
 */
public class CasillaPropiedad extends Casilla {

    private final int price;
    private final String colorGroup;
    private final int rentWithoutGroup;
    private final int rentWithGroup;

    // Changed by a background thread and read by the screen.
    private volatile Player owner;

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
    public void onLand(Player player, Game game) {
        Player currentOwner = owner;

        if (currentOwner == null) {
            offerPurchase(player, game);
        } else if (!currentOwner.equals(player)) {
            payRent(player, currentOwner, game);
        }
        // Landing on your own property has no effect.
    }

    private void offerPurchase(Player player, Game game) {
        GameLog log = game.getLog();
        Bank bank = game.getBank();

        // The player must keep some money after buying (reaching 0 means losing).
        if (bank.getBalance(player) <= price) {
            log.add(log.nameOf(player) + " no tiene dinero suficiente para comprar " + getName() + ".");
            return;
        }

        if (!game.askToBuy(player, this)) {
            log.add("El " + log.lowerNameOf(player) + " no ha comprado.");
            return;
        }

        log.add("El " + log.lowerNameOf(player) + " compra " + getName() + " por " + price + ".");
        log.add("Confirme su transacción.");
        game.confirmTransaction(() -> bank.removeMoney(player, price));
        setOwner(player);
    }

    private void payRent(Player player, Player propertyOwner, Game game) {
        GameLog log = game.getLog();
        Bank bank = game.getBank();

        int rent = getRent(ownerHasFullGroup(propertyOwner, game.getBoard()));
        String payer = log.nameOf(player);
        String ownerName = log.nameOf(propertyOwner);

        // Not enough money (or it would be left at 0): the player loses.
        if (bank.getBalance(player) <= rent) {
            log.add(payer + " debe pagar " + rent + " de renta a " + ownerName
                    + " por " + getName() + " pero no tiene dinero suficiente.");
            game.eliminatePlayer(player);
            return;
        }

        log.add(payer + " paga " + rent + " de renta a " + ownerName + " por " + getName() + ".");
        log.add("Confirme su transacción.");
        game.confirmTransaction(() -> bank.transfer(player, propertyOwner, rent));
    }

    // True if the player owns every property of this square's color group.
    private boolean ownerHasFullGroup(Player propertyOwner, Board board) {
        for (CasillaPropiedad property : board.getPropertiesByColorGroup(colorGroup)) {
            if (!propertyOwner.equals(property.getOwner())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String describe() {
        Player currentOwner = owner;
        String ownerName = currentOwner != null ? currentOwner.toString() : "none";
        return getName() + " [" + colorGroup + "] price=" + price
                + ", rent(no group)=" + rentWithoutGroup
                + ", rent(with group)=" + rentWithGroup
                + ", owner=" + ownerName;
    }
}
