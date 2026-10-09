import java.io.IOException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * INICIAR ESTE ARCHIVO EN LA PC ORGANIZADORA.
 * Mantiene un único Game oficial en Servidor.java y conecta Pico W + clientes TCP.
 * ServidorHardware sin cambios: 5000. Pico: 5001. Servidor de partida: 6000.
 */
public final class MonopolyDistribuidoApp {
    public static void main(String[] args) {
        int puerto = 6000;
        int jugadores = 4;
        int maxTurnos = 100;
        for (String a : args) {
            if (a.startsWith("--puerto=")) puerto = Integer.parseInt(a.substring(9));
            if (a.startsWith("--jugadores=")) jugadores = Integer.parseInt(a.substring(12));
            if (a.startsWith("--turnos=")) maxTurnos = Integer.parseInt(a.substring(9));
        }
        final int puertoPartida = puerto;
        try {
            // Servidor TCP de juego: fuente de verdad para turnos, posiciones y saldos.
            RfidSocketReader rfid = new RfidSocketReader(null, 30_000L);
            Servidor servidor = new Servidor(puertoPartida, jugadores, maxTurnos,
                    Game.TURN_TIME_SECONDS, rfid,
                    (n, lector) -> new MotorJuegoReal(new Game(n, lector)));
            servidor.activarDadosFisicos();
            servidor.iniciar();

            // Generador original de los dados, sin modificar.
            Thread generador = new Thread(() -> ServidorHardware.main(new String[0]),
                    "ServidorHardware-5000");
            generador.setDaemon(true);
            generador.start();

            // Compatible con el main.py de la Pico: los mismos comandos y respuestas.
            PicoHardwareBridge puente = new PicoHardwareBridge("127.0.0.1", 5000, 5001);
            puente.validarTirada(servidor::validarTiradaFisica);
            puente.alTirarDados((d1, d2) -> {
                String error = servidor.aplicarTiradaFisica(d1, d2);
                if (error != null) System.err.println("Tirada fisica rechazada: " + error);
            });
            puente.alEscanearTarjeta(uid -> {
                try { rfid.presentar(Long.parseLong(uid)); }
                catch (NumberFormatException ex) { System.err.println("UID invalido: " + uid); }
            });
            puente.iniciar();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { puente.close(); } catch (Exception ignored) { }
                servidor.detener();
            }));
            System.out.println("=========================================");
            System.out.println("MONOPOLY DISTRIBUIDO INICIADO");
            System.out.println("ServidorHardware : TCP 5000 (local)");
            System.out.println("Pico W           : TCP 5001");
            System.out.println("Clientes         : TCP " + puertoPartida);
            System.out.println("Jugadores        : " + jugadores + " (incluye organizador)");
            System.out.println("Abre HalloweenClienteRed en otras PCs, con la IP del organizador.");
            System.out.println("=========================================");
            SwingUtilities.invokeLater(() -> {
                HalloweenClienteRed ventana = new HalloweenClienteRed(
                        "127.0.0.1", puertoPartida, "Organizador");
                ventana.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
                ventana.setVisible(true);
            });
        } catch (IOException | IllegalArgumentException ex) {
            System.err.println("No se pudo iniciar el sistema: " + ex.getMessage());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Error iniciando Monopoly: " + ex.getMessage(), "Inicio", JOptionPane.ERROR_MESSAGE));
        }
    }
}
