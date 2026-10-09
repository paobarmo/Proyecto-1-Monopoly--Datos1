import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Conexion compatible con main.py SIN modificar ServidorHardware:
 * Pico -> TCP 5001 -> servidor original TCP 5000 -> Pico,
 *                       y publica dados / UID a las clases de Monopoly.
 */
public final class PicoHardwareBridge implements AutoCloseable {
    private final String hostOriginal;
    private final int puertoOriginal;
    private final int puertoPico;
    private ServerSocket entradaPico;
    private volatile boolean activo;
    private volatile BiConsumer<Integer, Integer> dados = (a, b) -> {};
    private volatile Consumer<String> rfid = uid -> {};
    private volatile Supplier<String> validarDados = () -> null;
    private volatile Runnable falloDados = () -> {};

    public PicoHardwareBridge(String hostOriginal, int puertoOriginal, int puertoPico) {
        this.hostOriginal = hostOriginal;
        this.puertoOriginal = puertoOriginal;
        this.puertoPico = puertoPico;
    }

    public void alTirarDados(BiConsumer<Integer, Integer> accion) { dados = accion; }
    public void alEscanearTarjeta(Consumer<String> accion) { rfid = accion; }
    /** Devuelve null si permite el lanzamiento, o ERROR|... si no se permite. */
    public void validarTirada(Supplier<String> validador) { validarDados = validador; }
    /** Permite liberar el turno si no hubo resultado oficial. */
    public void alFallarTirada(Runnable accion) { falloDados = accion; }

    public synchronized void iniciar() throws IOException {
        if (activo) return;
        entradaPico = new ServerSocket(puertoPico);
        activo = true;
        Thread hilo = new Thread(this::aceptar, "Puente-Pico-5001");
        hilo.setDaemon(true);
        hilo.start();
        System.out.println("PUENTE LISTO: Pico :" + puertoPico + " -> ServidorHardware "
                + hostOriginal + ":" + puertoOriginal);
    }

    private void aceptar() {
        while (activo) {
            try {
                Socket socket = entradaPico.accept();
                Thread hilo = new Thread(() -> atender(socket), "Puente-Pico-cliente");
                hilo.setDaemon(true);
                hilo.start();
            } catch (IOException e) {
                if (activo) System.err.println("Error esperando Pico: " + e);
            }
        }
    }

    private void atender(Socket socketPico) {
        boolean dadosPermitidos = false;
        boolean dadosPublicados = false;
        try (Socket pico = socketPico) {
            pico.setSoTimeout(5000);
            BufferedReader entrada = new BufferedReader(new InputStreamReader(
                    pico.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter salida = new PrintWriter(new OutputStreamWriter(
                    pico.getOutputStream(), StandardCharsets.UTF_8), true);

            String comando = entrada.readLine();
            if (comando == null) return;
            comando = comando.trim();
            boolean sonDados = comando.equals("TIRAR_DADOS");
            boolean esRFID = comando.matches("RFID\\|[0-9]{1,19}");
            if (!sonDados && !esRFID) {
                salida.println("ERROR|FORMATO_INVALIDO");
                return;
            }
            if (esRFID) {
                try { Long.parseLong(comando.substring(5)); }
                catch (NumberFormatException fueraDeRango) {
                    salida.println("ERROR|UID_FUERA_DE_RANGO");
                    return;
                }
            }
            if (sonDados) {
                String error = validarDados.get();
                if (error != null) {
                    salida.println(error); // main.py ya tiene rama ERROR.
                    return;
                }
                dadosPermitidos = true;
            }

            String respuesta = reenviarAlServidor(comando);
            salida.println(respuesta); // Se conserva DADOS|x|y o RFID_OK|uid.

            if (sonDados) {
                String[] valores = respuesta.split("\\|");
                if (valores.length == 3 && valores[0].equals("DADOS")) {
                    int d1 = Integer.parseInt(valores[1]);
                    int d2 = Integer.parseInt(valores[2]);
                    if (d1 >= 1 && d1 <= 6 && d2 >= 1 && d2 <= 6) {
                        dados.accept(d1, d2);
                        dadosPublicados = true;
                    }
                }
            } else {
                String uid = comando.substring(5);
                if (respuesta.equals("RFID_OK|" + uid)) rfid.accept(uid);
            }
        } catch (Exception e) {
            System.err.println("No se pudo procesar evento de hardware: " + e);
        } finally {
            if (dadosPermitidos && !dadosPublicados) falloDados.run();
        }
    }

    private String reenviarAlServidor(String comando) throws IOException {
        IOException ultimo = null;
        // ServidorHardware puede estar arrancando al mismo tiempo.
        for (int intento = 0; intento < 15; intento++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(hostOriginal, puertoOriginal), 1000);
                socket.setSoTimeout(5000);
                PrintWriter salida = new PrintWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader entrada = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8));
                salida.println(comando);
                String respuesta = entrada.readLine();
                if (respuesta == null) throw new IOException("ServidorHardware no respondio");
                return respuesta;
            } catch (IOException e) {
                ultimo = e;
                try { Thread.sleep(100); }
                catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Conexion interrumpida", e1);
                }
            }
        }
        throw new IOException("No conecta al ServidorHardware: " + ultimo);
    }

    @Override
    public synchronized void close() throws IOException {
        activo = false;
        if (entradaPico != null) entradaPico.close();
    }
}
