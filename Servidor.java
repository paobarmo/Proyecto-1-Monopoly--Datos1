import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servidor TCP del Monopoly (lo ejecuta el jugador organizador). Mantiene el estado oficial,
 * valida cada solicitud y actualiza a todos los clientes. El organizador juega conectándose
 * como cliente a localhost.
 *
 * Uso: java Servidor [puerto] [--hardware] [--jugadores=4] [--turnos=100]
 */
public class Servidor {
    /** Crea el motor del juego cuando ya están conectados todos los jugadores. */
    public interface FabricaMotor {
        MotorJuego crear(int jugadores, CardReader lector);
    }

    private final int puertoSolicitado;
    private final int nJugadores;
    private final int maxTurnos;
    private final int tiempoTurnoSeg;
    private final RfidSocketReader lector;
    private final FabricaMotor fabrica;

    private final Object lock = new Object();
    private final List<Manejador> jugadores = new ArrayList<>();
    private final List<Manejador> todos = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService reloj = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "reloj-turnos");
        t.setDaemon(true);
        return t;
    });

    private ServerSocket serverSocket;
    private ScheduledFuture<?> temporizador;
    private MotorJuego motor;
    private boolean iniciada;
    private boolean terminada;
    private boolean dadosLanzados;
    private final int[] avatars = {0, 1, 2, 3};
    private boolean pendienteCompra;
    private int numTurno;

    public Servidor(int puerto, int nJugadores, int maxTurnos, int tiempoTurnoSeg,
                    RfidSocketReader lector, FabricaMotor fabrica) {
        this.puertoSolicitado = puerto;
        this.nJugadores = nJugadores;
        this.maxTurnos = maxTurnos;
        this.tiempoTurnoSeg = tiempoTurnoSeg;
        this.lector = lector;
        this.fabrica = fabrica;
        this.lector.setAlEsperar(i -> broadcast("ESPERANDO_TARJETA|" + i + "|" + lector.getEsperaSegundos()));
    }

    public void iniciar() throws IOException {
        serverSocket = new ServerSocket(puertoSolicitado);
        // Hilo normal (no daemon): mantiene vivo el programa mientras el servidor escucha.
        Thread t = new Thread(this::aceptar, "aceptar-conexiones");
        t.start();
    }

    public int getPuerto() {
        return serverSocket.getLocalPort();
    }

    public void detener() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // cerrando
        }
        for (Manejador m : todos) {
            m.cerrar();
        }
        reloj.shutdownNow();
    }

    private void aceptar() {
        while (!serverSocket.isClosed()) {
            try {
                Socket s = serverSocket.accept();
                Manejador m = new Manejador(s);
                Thread t = new Thread(m, "cliente-" + s.getRemoteSocketAddress());
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("Error aceptando conexión: " + e.getMessage());
                }
            }
        }
    }

    // ------------------------------------------------------------------ envío

    private void broadcast(String msg) {
        for (Manejador m : todos) {
            m.enviar(msg);
        }
    }

    // ------------------------------------------------------------------ conexión de jugadores

    private boolean registrarJugador(Manejador m, String nombreSolicitado) {
        synchronized (lock) {
            if (iniciada || jugadores.size() >= nJugadores) {
                m.enviar("ERROR|La partida está llena o ya inició");
                return false;
            }
            String base = Protocolo.limpiar(nombreSolicitado);
            if (base.isEmpty()) {
                base = "Jugador" + (jugadores.size() + 1);
            }
            String nombre = base;
            int n = 2;
            while (nombreEnUso(nombre)) {
                nombre = base + n++;
            }
            m.nombre = nombre;
            m.indice = jugadores.size();
            jugadores.add(m);
            todos.add(m);
            m.enviar("BIENVENIDO|" + m.indice + "|" + m.nombre);
            for (int i = 0; i < jugadores.size(); i++) {
                m.enviar("AVATAR|" + i + "|" + avatars[i]);
            }
            broadcast("LOBBY|" + jugadores.size() + "|" + nJugadores + "|" + nombres());
            if (jugadores.size() == nJugadores) {
                iniciarPartida();
            }
            return true;
        }
    }

    private boolean nombreEnUso(String nombre) {
        for (Manejador j : jugadores) {
            if (j.nombre.equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return false;
    }

    private String nombres() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < jugadores.size(); i++) {
            sb.append(i == 0 ? "" : ",").append(jugadores.get(i).nombre);
        }
        return sb.toString();
    }

    private void iniciarPartida() {
        motor = fabrica.crear(nJugadores, lector);
        for (int i = 0; i < nJugadores; i++) {
            motor.registrarNombre(i, jugadores.get(i).nombre);
        }
        motor.alLog(msg -> broadcast("LOG|" + Protocolo.soloUnaLinea(msg)));
        iniciada = true;
        broadcast("INICIO|" + nombres());
        broadcast(estado());
        empezarTurno();
    }

    private void desconectar(Manejador m) {
        todos.remove(m);
        m.cerrar();
        synchronized (lock) {
            if (m.indice < 0 || !jugadores.contains(m)) {
                return;
            }
            if (!iniciada) {
                jugadores.remove(m);
                for (int i = 0; i < jugadores.size(); i++) {
                    jugadores.get(i).indice = i;
                    jugadores.get(i).enviar("BIENVENIDO|" + i + "|" + jugadores.get(i).nombre);
                }
                broadcast("LOBBY|" + jugadores.size() + "|" + nJugadores + "|" + nombres());
                return;
            }
            if (terminada || !motor.activo(m.indice)) {
                return;
            }
            boolean eraSuTurno = motor.turnoActual() == m.indice;
            motor.eliminar(m.indice);
            broadcast("EVENTO|" + m.nombre + " se desconectó y queda eliminado");
            broadcast("ELIMINADO|" + m.indice + "|" + m.nombre);
            broadcast(estado());
            if (eraSuTurno) {
                avanzar();
            } else {
                verificarFin(false);
            }
        }
    }

    // ------------------------------------------------------------------ turnos

    private void empezarTurno() {
        dadosLanzados = false;
        pendienteCompra = false;
        numTurno++;
        int actual = motor.turnoActual();
        lector.limpiar();
        lector.setJugadorActual(actual);
        broadcast("TURNO|" + actual + "|" + jugadores.get(actual).nombre + "|" + numTurno);
        programarTemporizador(numTurno);
    }

    private void programarTemporizador(int turno) {
        cancelarTemporizador();
        temporizador = reloj.schedule(() -> {
            synchronized (lock) {
                if (!terminada && turno == numTurno) {
                    broadcast("EVENTO|Se acabó el tiempo de " + jugadores.get(motor.turnoActual()).nombre);
                    avanzar();
                }
            }
        }, tiempoTurnoSeg, TimeUnit.SECONDS);
    }

    private void cancelarTemporizador() {
        if (temporizador != null) {
            temporizador.cancel(false);
        }
    }

    /** Pasa al siguiente jugador activo. Debe llamarse con el lock tomado. */
    private void avanzar() {
        cancelarTemporizador();
        motor.terminarTurno();
        for (int i = 0; i < nJugadores && !motor.activo(motor.turnoActual()); i++) {
            motor.terminarTurno();
        }
        if (verificarFin(true)) {
            return;
        }
        empezarTurno();
        broadcast(estado());
    }

    /** Revisa las condiciones de fin de partida. Debe llamarse con el lock tomado. */
    private boolean verificarFin(boolean contarLimite) {
        int activos = 0;
        int ultimo = -1;
        for (int i = 0; i < nJugadores; i++) {
            if (motor.activo(i)) {
                activos++;
                ultimo = i;
            }
        }
        String motivo = null;
        int ganador = ultimo;
        if (activos <= 1) {
            motivo = "UNICO_JUGADOR";
        } else if (contarLimite && numTurno >= maxTurnos) {
            motivo = "LIMITE_TURNOS";
            ganador = -1;
            for (int i = 0; i < nJugadores; i++) {
                if (motor.activo(i) && (ganador < 0 || motor.patrimonio(i) > motor.patrimonio(ganador))) {
                    ganador = i;
                }
            }
        }
        if (motivo == null) {
            return false;
        }
        terminada = true;
        cancelarTemporizador();
        broadcast(estado());
        String nombre = ganador >= 0 ? jugadores.get(ganador).nombre : "nadie";
        int patrimonio = ganador >= 0 ? motor.patrimonio(ganador) : 0;
        broadcast("FIN|" + ganador + "|" + nombre + "|" + motivo + "|" + patrimonio);
        return true;
    }

    private String estado() {
        StringBuilder sb = new StringBuilder("ESTADO|").append(motor.turnoActual()).append('|');
        for (int i = 0; i < nJugadores; i++) {
            sb.append(i == 0 ? "" : ";")
                    .append(i).append(',').append(jugadores.get(i).nombre).append(',')
                    .append(motor.saldo(i)).append(',').append(motor.posicion(i)).append(',')
                    .append(motor.activo(i)).append(',').append(motor.turnosCarcel(i));
        }
        // Campo extra al final: dueños de las propiedades ("posicion:indice,...", vacío si ninguna).
        sb.append('|').append(motor.propietarios());
        return sb.toString();
    }

    // ------------------------------------------------------------------ comandos

    private void procesar(Manejador m, String linea) {
        String[] p = linea.trim().split("\\|", -1);
        String cmd = p[0].toUpperCase();
        try {
            if (m.dispositivo) {
                comandoDispositivo(m, cmd, p);
                return;
            }
            synchronized (lock) {
                switch (cmd) {
                    case Protocolo.ELEGIR_AVATAR:
                        if (m.indice < 0 || p.length < 2) {
                            throw new ErrorJuego("Avatar inválido");
                        }
                        int avatar = Integer.parseInt(p[1]);
                        if (avatar < 0 || avatar >= 5) {
                            throw new ErrorJuego("Avatar inválido");
                        }
                        avatars[m.indice] = avatar;
                        broadcast("AVATAR|" + m.indice + "|" + avatar);
                        break;
                    case Protocolo.TIRAR_DADOS:
                        tirarDados(m);
                        break;
                    case Protocolo.COMPRAR_PROPIEDAD:
                        comprar(m);
                        break;
                    case Protocolo.NO_COMPRAR:
                        noComprar(m);
                        break;
                    case Protocolo.TERMINAR_TURNO:
                        exigirTurno(m);
                        if (!dadosLanzados) {
                            throw new ErrorJuego("Debes lanzar los dados antes de terminar el turno");
                        }
                        m.enviar("OK|TURNO_TERMINADO");
                        avanzar();
                        break;
                    case Protocolo.TERMINAR_PARTIDA:
                        terminarAnticipada(m);
                        break;
                    case Protocolo.CONSULTAR_ESTADO:
                        if (iniciada) {
                            m.enviar(estado());
                        } else {
                            m.enviar("LOBBY|" + jugadores.size() + "|" + nJugadores + "|" + nombres());
                        }
                        break;
                    case Protocolo.CONSULTAR_TRANSACCIONES:
                        consultarTransacciones(m);
                        break;
                    default:
                        throw new ErrorJuego("Comando desconocido: " + Protocolo.limpiar(cmd));
                }
            }
        } catch (ErrorJuego e) {
            m.enviar("ERROR|" + Protocolo.soloUnaLinea(e.getMessage()));
        } catch (RuntimeException e) {
            e.printStackTrace();
            m.enviar("ERROR|Error interno del servidor: " + Protocolo.soloUnaLinea(e.getMessage()));
        }
    }

    private void comandoDispositivo(Manejador m, String cmd, String[] p) {
        if (!cmd.equals(Protocolo.RFID) || p.length < 2) {
            m.enviar("ERROR|El dispositivo solo puede enviar RFID|uid");
            return;
        }
        try {
            lector.presentar(Long.parseLong(p[1].trim()));
            m.enviar("OK|RFID");
        } catch (NumberFormatException e) {
            m.enviar("ERROR|uid inválido");
        }
    }

    private void exigirTurno(Manejador m) throws ErrorJuego {
        if (!iniciada) {
            throw new ErrorJuego("La partida aún no inicia");
        }
        if (terminada) {
            throw new ErrorJuego("La partida ya terminó");
        }
        if (m.indice != motor.turnoActual()) {
            throw new ErrorJuego("No es tu turno");
        }
    }

    private void tirarDados(Manejador m) throws ErrorJuego {
        exigirTurno(m);
        if (dadosLanzados) {
            throw new ErrorJuego("Ya lanzaste los dados en este turno");
        }
        dadosLanzados = true;
        MotorJuego.Lanzamiento l = motor.lanzar();
        broadcast("DADOS|" + m.indice + "|" + l.d1 + "|" + l.d2 + "|" + l.desde + "|" + l.hasta
                + "|" + Protocolo.limpiar(l.casilla));
        MotorJuego.Resolucion r = motor.resolver();
        boolean huboEliminacion = false;
        if (r.eliminado) {
            broadcast("ELIMINADO|" + m.indice + "|" + m.nombre);
            huboEliminacion = true;
        }
        huboEliminacion |= eliminarSinSaldo();   // saldo 0 tras pagar renta, etc.
        boolean yoEliminado = r.eliminado || !motor.activo(m.indice);
        pendienteCompra = r.puedeComprar && !yoEliminado;
        broadcast(estado());
        m.enviar("OK|DADOS");
        if (yoEliminado) {
            if (!verificarFin(false)) {
                avanzar();
            }
        } else if (huboEliminacion && verificarFin(false)) {
            // La partida terminó: ya se envió FIN.
        } else if (pendienteCompra) {
            m.enviar("PUEDE_COMPRAR|" + Protocolo.limpiar(l.casilla) + "|" + r.precio);
        }
    }

    private void comprar(Manejador m) throws ErrorJuego {
        exigirTurno(m);
        if (!dadosLanzados) {
            throw new ErrorJuego("Primero lanza los dados");
        }
        if (!pendienteCompra) {
            throw new ErrorJuego("No hay una propiedad disponible para comprar");
        }
        String texto = motor.comprar();
        pendienteCompra = false;
        broadcast("COMPRA|" + m.indice + "|" + Protocolo.soloUnaLinea(texto));
        boolean huboEliminacion = eliminarSinSaldo();   // comprar con todo el dinero -> saldo 0
        broadcast(estado());
        m.enviar("OK|COMPRA");
        if (huboEliminacion && !verificarFin(false) && !motor.activo(m.indice)) {
            avanzar();
        }
    }

    private void noComprar(Manejador m) throws ErrorJuego {
        exigirTurno(m);
        if (!pendienteCompra) {
            throw new ErrorJuego("No hay una compra pendiente");
        }
        pendienteCompra = false;
        broadcast("EVENTO|" + m.nombre + " decidió no comprar");
        m.enviar("OK|NO_COMPRAR");
    }

    private void consultarTransacciones(Manejador m) throws ErrorJuego {
        if (!iniciada) {
            throw new ErrorJuego("La partida aún no inicia");
        }
        List<String> h = motor.historial();
        m.enviar("TRANSACCIONES|" + h.size());
        for (int i = 0; i < h.size(); i++) {
            m.enviar("TX|" + (i + 1) + "|" + Protocolo.soloUnaLinea(h.get(i)));
        }
        m.enviar("FIN_TRANSACCIONES");
    }

    /** Elimina a todo jugador activo con saldo 0 (no hay saldos negativos). Con el lock tomado. */
    private boolean eliminarSinSaldo() {
        boolean alguno = false;
        for (int i = 0; i < nJugadores; i++) {
            if (motor.activo(i) && motor.saldo(i) <= 0) {
                motor.eliminar(i);
                broadcast("EVENTO|" + jugadores.get(i).nombre + " se quedó sin dinero y queda eliminado");
                broadcast("ELIMINADO|" + i + "|" + jugadores.get(i).nombre);
                alguno = true;
            }
        }
        return alguno;
    }

    /** ESC > Terminar: acaba la partida para todos; gana quien tenga más dinero (saldo). */
    private void terminarAnticipada(Manejador m) throws ErrorJuego {
        if (!iniciada) {
            throw new ErrorJuego("La partida aún no inicia");
        }
        if (terminada) {
            throw new ErrorJuego("La partida ya terminó");
        }
        if (m.indice < 0 || !motor.activo(m.indice)) {
            throw new ErrorJuego("Solo los jugadores que siguen en la partida pueden terminarla");
        }
        List<Integer> ganadores = new ArrayList<>();
        int mejor = -1;
        for (int i = 0; i < nJugadores; i++) {
            if (!motor.activo(i)) {
                continue;
            }
            int saldo = motor.saldo(i);
            if (saldo > mejor) {
                mejor = saldo;
                ganadores.clear();
                ganadores.add(i);
            } else if (saldo == mejor) {
                ganadores.add(i);
            }
        }
        terminada = true;
        cancelarTemporizador();
        broadcast("EVENTO|" + m.nombre + " terminó la partida antes de tiempo");
        broadcast(estado());
        StringBuilder nombre = new StringBuilder();
        for (int g : ganadores) {
            nombre.append(nombre.length() == 0 ? "" : " y ").append(jugadores.get(g).nombre);
        }
        int indice = ganadores.isEmpty() ? -1 : ganadores.get(0);
        broadcast("FIN|" + indice + "|" + (nombre.length() == 0 ? "nadie" : nombre)
                + "|TERMINADA_ANTES|" + Math.max(mejor, 0));
        m.enviar("OK|TERMINAR_PARTIDA");
    }

    // ------------------------------------------------------------------ una conexión

    private class Manejador implements Runnable {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;
        volatile int indice = -1;
        volatile String nombre = "";
        volatile boolean dispositivo;

        Manejador(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void enviar(String msg) {
            synchronized (out) {
                out.println(msg);
            }
        }

        void cerrar() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // ya cerrado
            }
        }

        @Override
        public void run() {
            try {
                String linea = in.readLine();
                if (linea == null || !saludo(linea)) {
                    return;
                }
                while ((linea = in.readLine()) != null) {
                    if (!linea.trim().isEmpty()) {
                        procesar(this, linea);
                    }
                }
            } catch (IOException ignored) {
                // desconexión
            } finally {
                desconectar(this);
            }
        }

        private boolean saludo(String linea) {
            String[] p = linea.trim().split("\\|", -1);
            if (p[0].equalsIgnoreCase(Protocolo.CONECTAR)) {
                return registrarJugador(this, p.length > 1 ? p[1] : "");
            }
            if (p[0].equalsIgnoreCase(Protocolo.DISPOSITIVO)) {
                dispositivo = true;
                todos.add(this);
                enviar("OK|DISPOSITIVO");
                return true;
            }
            enviar("ERROR|El primer mensaje debe ser CONECTAR|nombre o DISPOSITIVO");
            return false;
        }
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        int puerto = 5000;
        int jugadores = 4;
        int turnos = 100;
        boolean hardware = false;
        for (String a : args) {
            if (a.equals("--hardware")) {
                hardware = true;
            } else if (a.startsWith("--jugadores=")) {
                jugadores = Integer.parseInt(a.substring(12));
            } else if (a.startsWith("--turnos=")) {
                turnos = Integer.parseInt(a.substring(9));
            } else {
                puerto = Integer.parseInt(a);
            }
        }
        // Mismos IDs que Game.CARD_IDS (jugador 1..4); solo se usan en modo simulado.
        long[] tarjetas = {57482001L, 2209842189L, 123L, 321L};
        RfidSocketReader lector = new RfidSocketReader(hardware ? null : tarjetas, 30_000);
        Servidor s = new Servidor(puerto, jugadores, turnos, Game.TURN_TIME_SECONDS, lector,
                (n, l) -> new MotorJuegoReal(new Game(n, l)));
        s.iniciar();
        System.out.println("Servidor Monopoly escuchando en el puerto " + s.getPuerto()
                + (hardware ? " (lector RFID físico)" : " (RFID simulado)")
                + ", esperando " + jugadores + " jugadores.");
        System.out.println("Direcciones para los clientes:");
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            Enumeration<InetAddress> ips = ni.getInetAddresses();
            while (ni.isUp() && !ni.isLoopback() && ips.hasMoreElements()) {
                InetAddress ip = ips.nextElement();
                if (ip instanceof java.net.Inet4Address) {
                    System.out.println("  " + ip.getHostAddress());
                }
            }
        }
    }
}