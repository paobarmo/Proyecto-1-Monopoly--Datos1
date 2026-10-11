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
        return game.getPlayer(i).enJuego;
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
            game.triggerLanded(llegada);
            if (llegada instanceof CasillaPropiedad && !((CasillaPropiedad) llegada).isOwned()) {
                r.puedeComprar = true;
                r.precio = ((CasillaPropiedad) llegada).getPrice();
            }
        } catch (IllegalStateException e) {
            // Bank.checkFunds: no puede cubrir un pago obligatorio -> eliminado
            game.getLog().add(game.getLog().nameOf(game.getPlayer(idx))
                    + " no puede cubrir un pago obligatorio y queda eliminado.");
            eliminar(idx);
            r.eliminado = true;
        }
        return r;
    }

    @Override
    public String comprar() throws ErrorJuego {
        Player p = game.getCurrentPlayer();
        Bank bank = game.getBank();
        Casilla c = game.getBoard().getCurrentCasilla(p);
        if (!(c instanceof CasillaPropiedad)) {
            throw new ErrorJuego("Esta casilla no se puede comprar");
        }
        CasillaPropiedad prop = (CasillaPropiedad) c;
        if (prop.isOwned()) {
            throw new ErrorJuego("La propiedad ya tiene propietario");
        }
        if (bank.getBalance(p) < prop.getPrice()) {
            throw new ErrorJuego("Saldo insuficiente: cuesta " + prop.getPrice()
                    + " y tienes " + bank.getBalance(p));
        }
        if (!bank.removeMoney(p, prop.getPrice())) {
            throw new ErrorJuego("Pago no confirmado: tarjeta RFID no válida o no presentada");
        }
        prop.setOwner(p);
        String msg = game.getLog().nameOf(p) + " compró " + prop.getName() + " por " + prop.getPrice();
        game.getLog().add(msg);
        return msg;
    }

    @Override
    public void terminarTurno() {
        game.endTurn();
    }

    @Override
    public void eliminar(int i) {
        Player p = game.getPlayer(i);
        p.enJuego = false;
        Board b = game.getBoard();
        for (int k = 0; k < b.getSize(); k++) {
            Casilla c = b.getCasilla(k);
            if (c instanceof CasillaPropiedad && p.equals(((CasillaPropiedad) c).getOwner())) {
                ((CasillaPropiedad) c).setOwner(null);
            }
        }
    }

    @Override
    public List<String> historial() {
        return game.getLog().getMessages();
    }
}
