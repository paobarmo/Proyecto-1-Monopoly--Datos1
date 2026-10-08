import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Entrada nueva a Monopoly para recibir dados y RFID de la Pico.
 * NO cambia MonopolyApp, Game, GamePanel, Bank ni ServidorHardware.
 *
 * Ejecutar: java MonopolyHardwareApp
 */
public final class MonopolyHardwareApp {
    private static final String MENU = "MENU";
    private static final String JUGADORES = "PLAYERS";
    private static final String PARTIDA = "GAME";

    public static void main(String[] args) {
        // Conservamos el ServidorHardware original y su puerto 5000.
        Thread servidorOriginal = new Thread(
                () -> ServidorHardware.main(new String[0]), "ServidorHardware-original-5000");
        servidorOriginal.setDaemon(true);
        servidorOriginal.start();

        HardwareCardReader lector = new HardwareCardReader();
        AtomicReference<GamePanel> panelActual = new AtomicReference<>();
        AtomicBoolean tiradaEnCurso = new AtomicBoolean(false);
        PicoHardwareBridge puente = new PicoHardwareBridge("127.0.0.1", 5000, 5001);

        // El puente pasa el UID al CardReader que recibe Game y utiliza Bank.
        puente.alEscanearTarjeta(lector::recibirUID);

        // Solo aceptar una tirada durante el movimiento del turno; sin partida, rechazar.
        puente.validarTirada(() -> {
            if (panelActual.get() == null) return "ERROR|PARTIDA_NO_INICIADA";
            return tiradaEnCurso.compareAndSet(false, true)
                    ? null : "ERROR|TIRADA_EN_PROCESO";
        });
        puente.alFallarTirada(() -> tiradaEnCurso.set(false));

        // GamePanel.applyRoll realmente recibe (int steps, Runnable onFinished).
        // Es la SUMA de los dados oficiales, no una nueva tirada aleatoria.
        puente.alTirarDados((d1, d2) -> SwingUtilities.invokeLater(() -> {
            GamePanel panel = panelActual.get();
            if (panel == null) {
                tiradaEnCurso.set(false);
                return;
            }
            try {
                panel.applyRoll(d1 + d2, () -> {
                    // GamePanel llama este callback al finalizar animacion y cambiar turno.
                    tiradaEnCurso.set(false);
                });
            } catch (RuntimeException e) {
                tiradaEnCurso.set(false);
                System.err.println("Error aplicando dados en GamePanel: " + e);
                e.printStackTrace();
            }
        }));

        try {
            puente.iniciar();
        } catch (Exception e) {
            System.err.println("No se pudo iniciar el puente de la Pico: " + e.getMessage());
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { puente.close(); } catch (Exception ignorada) { }
        }));

        SwingUtilities.invokeLater(() -> mostrarVentana(lector, panelActual));
    }

    private static void mostrarVentana(HardwareCardReader lector,
                                     AtomicReference<GamePanel> panelActual) {
        JFrame ventana = new JFrame("Monopoly Halloween (Pico W)");
        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        CardLayout layout = new CardLayout();
        JPanel pantallas = new JPanel(layout);
        AtomicBoolean iniciando = new AtomicBoolean(false);

        PlayerSelectionPanel seleccion = new PlayerSelectionPanel();
        seleccion.setOnStart(cantidad -> {
            if (!iniciando.compareAndSet(false, true)) return;
            // Usamos la MISMA clase Game; la unica diferencia es el CardReader fisico.
            // El hilo evita bloquear la interfaz si el constructor tarda.
            Thread creacion = new Thread(() -> {
                try {
                    Game juego = new Game(cantidad, lector);
                    SwingUtilities.invokeLater(() -> {
                        GamePanel panel = new GamePanel(juego);
                        panelActual.set(panel);
                        pantallas.add(panel, PARTIDA);
                        layout.show(pantallas, PARTIDA);
                        pantallas.revalidate();
                        pantallas.repaint();
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        iniciando.set(false);
                        JOptionPane.showMessageDialog(ventana,
                                "No se pudo iniciar la partida: " + e.getMessage());
                    });
                    e.printStackTrace();
                }
            }, "Crear-partida-Monopoly");
            creacion.setDaemon(true);
            creacion.start();
        });

        pantallas.add(new MainMenuPanel(() -> layout.show(pantallas, JUGADORES)), MENU);
        pantallas.add(seleccion, JUGADORES);
        ventana.setContentPane(pantallas);
        ventana.setMinimumSize(new Dimension(900, 700));
        ventana.setExtendedState(JFrame.MAXIMIZED_BOTH);
        ventana.setVisible(true);
    }
}
