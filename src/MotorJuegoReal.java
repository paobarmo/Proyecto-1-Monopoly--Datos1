import java.util.List;
import java.util.function.Consumer;

/** Adaptador entre el servidor y las clases del juego (Game, Bank, Board, CasillaPropiedad). */
public class MotorJuegoReal implements MotorJuego {
    private final Game game;
    private final Dado dado = new Dado();
    private MoveResult ultimoMovimiento;

    public MotorJuegoReal(Game game) {
        this.game = game;
        // Player no inicializa enJuego en su constructor (queda en false): se activa aquí.
        for (int i = 0; i < game.getPlayerCount(); i++) {
            game.getPlayer(i).enJuego = true;
        }
    }

    @Override
    public int cantidad() {
        return game.getPlayerCount();
    }

    @Override
    public int turnoActual() {
        return game.getCurrentPlayerIndex();
    }

    @Override
    public boolean activo(int i) {
        return game.getPlayer(i).enJuego && !game.isEliminated(i);
    }

    @Override
    public int saldo(int i) {
        return game.getBank().getBalance(game.getPlayer(i));
    }

    @Override
    public int posicion(int i) {
        return game.getPlayerPosition(i);
    }

    @Override
    public int patrimonio(int i) {
        Player p = game.getPlayer(i);
        int total = saldo(i);
        Board b = game.getBoard();
        for (int k = 0; k < b.getSize(); k++) {
            Casilla c = b.getCasilla(k);
            if (c instanceof CasillaPropiedad && p.equals(((CasillaPropiedad) c).getOwner())) {
                total += ((CasillaPropiedad) c).getPrice();
            }
        }
        return total;
    }

    @Override
    public void registrarNombre(int i, String nombre) {
        Player p = game.getPlayer(i);
        p.nombre = nombre;
        game.getLog().registerPlayer(p, nombre);
    }

    @Override
    public void alLog(Consumer<String> oyente) {
        game.getLog().addListener(oyente);
    }

    @Override
    public Lanzamiento lanzar() {
        int idx = game.getCurrentPlayerIndex();
        Lanzamiento l = new Lanzamiento();
        l.desde = game.getPlayerPosition(idx);
        l.d1 = dado.lanzar();
        l.d2 = dado.lanzar();
        ultimoMovimiento = game.moveCurrentPlayer(l.d1 + l.d2);
        l.hasta = game.getPlayerPosition(idx);
        l.casilla = ultimoMovimiento.getLandingSquare().getName();
        return l;
    }

    @Override
    public Resolucion resolver() {
        Resolucion r = new Resolucion();
        int idx = game.getCurrentPlayerIndex();
        try {
            for (Casilla c : ultimoMovimiento.getPassedSquares()) {
                game.triggerPassed(c);
            }
            Casilla llegada = ultimoMovimiento.getLandingSquare();
            // triggerLanded elimina al jugador si no puede pagar la renta o los impuestos.
            game.triggerLanded(llegada);
            game.eliminateBankruptPlayers();   // saldo 0 -> pierde
            if (game.isEliminated(idx)) {
                r.eliminado = true;
                return r;
            }
            if (llegada instanceof CasillaPropiedad && !((CasillaPropiedad) llegada).isOwned()) {
                r.puedeComprar = true;
                r.precio = ((CasillaPropiedad) llegada).getPrice();
            }
        } catch (IllegalStateException e) {
            // Red de seguridad (Bank.checkFunds): no puede cubrir un pago obligatorio -> eliminado
            game.eliminatePlayer(idx, "no puede cubrir un pago obligatorio y queda eliminado");
            r.eliminado = true;
        }
        return r;
    }

    @Override
    public String comprar() throws ErrorJuego {
        // La lógica vive en Game para que el modo local y el de red usen el mismo código.
        return game.buyCurrentProperty();
    }

    @Override
    public String propietarios() {
        int[] owners = game.getPropertyOwnerIndexes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] >= 0) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(i).append(':').append(owners[i]);
            }
        }
        return sb.toString();
    }

    @Override
    public int turnosCarcel(int i) {
        return game.getJailTurnsLeft(i);
    }

    @Override
    public void terminarTurno() {
        game.endTurn();
    }

    @Override
    public void eliminar(int i) {
        game.getPlayer(i).enJuego = false;
        game.eliminatePlayer(i);   // sale de la cárcel y libera sus propiedades
    }

    @Override
    public List<String> historial() {
        return game.getLog().getMessages();
    }
}
