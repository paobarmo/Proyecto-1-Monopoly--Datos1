/** Contrato de los eventos utilizado por EventManager. */
@FunctionalInterface
public interface GameEvent {
    void execute(Player player, Bank bank, GameLog log);
}
