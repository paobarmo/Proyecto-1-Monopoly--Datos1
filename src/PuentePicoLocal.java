import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/**
 * Conecta la Pico con una partida LOCAL (todos en una laptop).
 * No reemplaza PuentePicoMonopoly, utilizado por Crear partida en red.
 */
public final class PuentePicoLocal implements AutoCloseable {
    private final GamePanel panel;
    private final RfidSocketReader lector;
    private final int puerto;
    private final Dado dado = new Dado();
    private final AtomicBoolean tiradaEnCurso = new AtomicBoolean();
    private volatile boolean activo;
    private ServerSocket servidor;

    public PuentePicoLocal(GamePanel panel, RfidSocketReader lector, int puerto) {
        this.panel = panel;
        this.lector = lector;
        this.puerto = puerto;
    }

    public synchronized void iniciar() throws IOException {
        servidor = new ServerSocket(puerto);
        activo = true;
        Thread hilo = new Thread(this::esperarConexiones, "Pico-local-aceptar");
        hilo.setDaemon(true);
        hilo.start();
        System.out.println("Pico W local lista en TCP " + puerto);
    }

    private void esperarConexiones() {
        while (activo) {
            try {
                Socket socket = servidor.accept();
                Thread hilo = new Thread(() -> procesar(socket), "Pico-local-evento");
                hilo.setDaemon(true);
                hilo.start();
            } catch (IOException e) {
                if (activo) System.err.println("Conexion Pico local: " + e.getMessage());
            }
        }
    }

    private void procesar(Socket socket) {
        try (Socket cliente = socket;
             BufferedReader entrada = new BufferedReader(new InputStreamReader(
                     cliente.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter salida = new PrintWriter(new OutputStreamWriter(
                     cliente.getOutputStream(), StandardCharsets.UTF_8), true)) {
            cliente.setSoTimeout(5000);
            String mensaje = entrada.readLine();
            if (mensaje == null) return;
            mensaje = mensaje.trim();
            if (mensaje.equals("TIRAR_DADOS")) {
                if (!activo || !tiradaEnCurso.compareAndSet(false, true)) {
                    salida.println("ERROR|TIRADA_EN_PROCESO");
                    return;
                }
                int a = dado.lanzar();
                int b = dado.lanzar();
                // Importante: responder ANTES del movimiento porque este puede esperar RFID.
                salida.println("DADOS|" + a + "|" + b);
                SwingUtilities.invokeLater(() -> {
                    try {
                        panel.applyRoll(a + b, () -> tiradaEnCurso.set(false));
                    } catch (RuntimeException ex) {
                        tiradaEnCurso.set(false);
                        System.err.println("No se pudo mover en local: " + ex.getMessage());
                    }
                });
            } else if (mensaje.startsWith("RFID|")) {
                String uid = mensaje.substring(5).trim();
                try {
                    long numero = Long.parseLong(uid);
                    if (numero < 0) throw new NumberFormatException("UID negativo");
                    lector.presentar(numero);
                    salida.println("RFID_OK|" + uid);
                } catch (NumberFormatException e) {
                    salida.println("ERROR|UID_INVALIDO");
                }
            } else {
                salida.println("ERROR|COMANDO_DESCONOCIDO");
            }
        } catch (IOException e) {
            if (activo) System.err.println("Error leyendo Pico en local: " + e.getMessage());
        }
    }

    @Override public synchronized void close() {
        activo = false;
        if (servidor != null) {
            try { servidor.close(); } catch (IOException ignorada) { }
        }
    }
}
