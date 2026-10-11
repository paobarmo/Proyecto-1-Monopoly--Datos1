import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * Cliente TCP. Solo envía solicitudes y entrega cada línea del servidor a un callback.
 * La interfaz gráfica (GamePanel, etc.) puede usar esta clase tal cual:
 *
 *   Cliente c = new Cliente("192.168.1.10", 5000, linea -> SwingUtilities.invokeLater(() -> ...));
 *   c.conectar("Ana");
 *   c.enviar(Protocolo.TIRAR_DADOS);
 *
 * Como programa de consola: java Cliente host puerto nombre
 */
public class Cliente implements Closeable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final Consumer<String> alMensaje;
    private final Consumer<IOException> alDesconectar;

    public Cliente(String host, int puerto, Consumer<String> alMensaje) throws IOException {
        this(host, puerto, alMensaje, e -> { });
    }

    public Cliente(String host, int puerto, Consumer<String> alMensaje, Consumer<IOException> alDesconectar)
            throws IOException {
        this.socket = new Socket(host, puerto);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.alMensaje = alMensaje;
        this.alDesconectar = alDesconectar;
    }

    /** Envía CONECTAR e inicia el hilo que lee los mensajes del servidor. */
    public void conectar(String nombre) {
        enviar(Protocolo.CONECTAR + "|" + Protocolo.limpiar(nombre));
        Thread t = new Thread(this::leer, "lector-servidor");
        t.setDaemon(true);
        t.start();
    }

    public void enviar(String mensaje) {
        synchronized (out) {
            out.println(mensaje);
        }
    }

    private void leer() {
        try {
            String linea;
            while ((linea = in.readLine()) != null) {
                alMensaje.accept(linea);
            }
            alDesconectar.accept(null);
        } catch (IOException e) {
            alDesconectar.accept(e);
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // ya cerrado
        }
    }

    // ------------------------------------------------------------------ consola

    /** Convierte un mensaje del protocolo en texto legible. */
    public static String formatear(String linea) {
        String[] p = linea.split("\\|", -1);
        switch (p[0]) {
            case "BIENVENIDO":
                return "Conectado como " + p[2] + " (jugador " + (Integer.parseInt(p[1]) + 1) + ")";
            case "LOBBY":
                return "Sala " + p[1] + "/" + p[2] + ": " + p[3];
            case "INICIO":
                return "¡Inicia la partida! Jugadores: " + p[1];
            case "TURNO":
                return "=== Turno #" + p[3] + ": " + p[2] + " ===";
            case "DADOS":
                return "Dados: " + p[2] + " + " + p[3] + " = " + (Integer.parseInt(p[2]) + Integer.parseInt(p[3]))
                        + " | casilla " + p[4] + " -> " + p[5] + " (" + p[6] + ")";
            case "PUEDE_COMPRAR":
                return "Puedes comprar " + p[1] + " por " + p[2] + "  [c = comprar, n = no comprar]";
            case "COMPRA":
                return "Compra: " + p[2];
            case "ESTADO":
                StringBuilder sb = new StringBuilder("Estado (turno de jugador " + (Integer.parseInt(p[1]) + 1) + "):");
                for (String j : p[2].split(";")) {
                    String[] d = j.split(",");
                    sb.append("\n   ").append(d[1]).append(": saldo ").append(d[2])
                      .append(", casilla ").append(d[3]).append(d[4].equals("true") ? "" : "  [ELIMINADO]");
                }
                return sb.toString();
            case "LOG":
                return "  · " + p[1];
            case "ESPERANDO_TARJETA":
                return "Acerca la tarjeta RFID (jugador " + (Integer.parseInt(p[1]) + 1) + ", " + p[2] + " s)";
            case "EVENTO":
                return "* " + p[1];
            case "ELIMINADO":
                return "** " + p[2] + " fue eliminado **";
            case "TRANSACCIONES":
                return "--- Historial (" + p[1] + ") ---";
            case "TX":
                return "  " + p[1] + ". " + p[2];
            case "FIN_TRANSACCIONES":
                return "-----";
            case "FIN":
                return "##### FIN DE LA PARTIDA: gana " + p[2] + " (" + p[3] + ", patrimonio " + p[4] + ") #####";
            case "OK":
                return "";
            case "ERROR":
                return "ERROR: " + p[1];
            default:
                return linea;
        }
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        String nombre = args.length > 2 ? args[2] : "Jugador";
        Cliente c = new Cliente(host, puerto, linea -> {
            String t = formatear(linea);
            if (!t.isEmpty()) {
                System.out.println(t);
            }
        }, e -> {
            System.out.println("Conexión cerrada por el servidor.");
            System.exit(0);
        });
        c.conectar(nombre);
        System.out.println("Comandos: t = tirar dados, c = comprar, n = no comprar, f = terminar turno, "
                + "e = estado, h = historial, q = salir");
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String l = sc.nextLine().trim().toLowerCase();
            switch (l) {
                case "t": c.enviar(Protocolo.TIRAR_DADOS); break;
                case "c": c.enviar(Protocolo.COMPRAR_PROPIEDAD); break;
                case "n": c.enviar(Protocolo.NO_COMPRAR); break;
                case "f": c.enviar(Protocolo.TERMINAR_TURNO); break;
                case "e": c.enviar(Protocolo.CONSULTAR_ESTADO); break;
                case "h": c.enviar(Protocolo.CONSULTAR_TRANSACCIONES); break;
                case "q": c.close(); return;
                default:
                    if (!l.isEmpty()) {
                        c.enviar(l.toUpperCase());
                    }
            }
        }
    }
}
