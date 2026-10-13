import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Puerta de entrada del hardware al Monopoly de tus compañeros.
 * La Pico usa puerto 5001; el juego y las otras laptops siguen en 5000.
 * No es otro banco ni otro generador de dados.
 */
public final class PuentePicoMonopoly implements AutoCloseable {
    private final Servidor juego;
    private final int puerto;
    private ServerSocket servidor;

    public PuentePicoMonopoly(Servidor juego, int puerto) {
        this.juego = juego;
        this.puerto = puerto;
    }

    public void iniciar() throws IOException {
        servidor = new ServerSocket(puerto);
        Thread hilo = new Thread(this::aceptar, "pico-aceptar-5001");
        hilo.setDaemon(true);
        hilo.start();
        System.out.println("Pico W lista: puerto TCP " + puerto);
    }

    private void aceptar() {
        while (servidor != null && !servidor.isClosed()) {
            try {
                Socket cliente = servidor.accept();
                Thread hilo = new Thread(() -> atender(cliente), "pico-evento");
                hilo.setDaemon(true);
                hilo.start();
            } catch (IOException e) {
                if (!servidor.isClosed()) System.err.println("Pico: " + e.getMessage());
            }
        }
    }

    private void atender(Socket socket) {
        try (Socket cliente = socket;
             BufferedReader entrada = new BufferedReader(new InputStreamReader(
                     cliente.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter salida = new PrintWriter(new OutputStreamWriter(
                     cliente.getOutputStream(), StandardCharsets.UTF_8), true)) {

            cliente.setSoTimeout(5000);
            String comando = entrada.readLine();
            if (comando == null) return;
            comando = comando.trim();

            if (comando.equals("TIRAR_DADOS")) {
                // Respuesta inmediata a la Pico antes de cualquier espera RFID.
                juego.solicitarDadosPico(salida::println);
            } else if (comando.startsWith("RFID|")) {
                String uid = comando.substring(5).trim();
                try {
                    long numero = Long.parseLong(uid);
                    if (numero < 0) throw new NumberFormatException("negativo");
                    juego.recibirRFIDPico(numero);
                    salida.println("RFID_OK|" + uid);
                } catch (NumberFormatException e) {
                    salida.println("ERROR|UID_INVALIDO");
                }
            } else {
                salida.println("ERROR|COMANDO_DESCONOCIDO");
            }
        } catch (IOException e) {
            System.err.println("Error de comunicación con la Pico: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (servidor != null) {
            try { servidor.close(); } catch (IOException ignorada) { }
        }
    }
}
