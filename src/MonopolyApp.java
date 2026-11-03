import java.awt.CardLayout;
import java.awt.Dimension;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class MonopolyApp {
    private static final String MENU_SCREEN = "MENU";
    private static final String PLAYERS_SCREEN = "PLAYERS";
    private static final String GAME_SCREEN = "GAME";
    // Arranque habitual: RFID simulado. Arranque con --hardware: Pico física.
    private static boolean usarHardware = false;

    public static void main(String[] args) {
        for (String arg : args) {
            if ("--hardware".equals(arg)) usarHardware = true;
        }
        SwingUtilities.invokeLater(MonopolyApp::createAndShowWindow);
    }

    private static void createAndShowWindow() {
        JFrame frame = new JFrame("Monopoly Halloween");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        CardLayout layout = new CardLayout();
        JPanel screens = new JPanel(layout);
        PlayerSelectionPanel selection = new PlayerSelectionPanel();

        selection.setOnStart(playerCount -> {
            int option = HalloweenDialogs.modo(frame);
            if (option == 2) {
                startLocal(frame, screens, layout, playerCount);
            } else if (option == 0 || option == 1) {
                boolean host = option == 0;
                String hostAddress = host ? "localhost" : HalloweenDialogs.entrada(frame,
                        "IP de la computadora que creó la partida:", "172.20.10.2");
                if (hostAddress == null || hostAddress.trim().isEmpty()) return;
                String name = HalloweenDialogs.entrada(frame, "Tu nombre de jugador:", "Jugador");
                if (name == null || name.trim().isEmpty()) return;
                if (host) {
                    boolean confirmation = HalloweenDialogs.confirmar(frame, "Crear partida",
                            "El servidor esperará " + playerCount + " jugadores.\n" +
                                    "Tus amigos deberán conectarse a tu dirección IPv4, puerto 5000.\n" +
                                    "¿Crear la partida?");
                    if (!confirmation) return;
                }
                connectNetwork(frame, screens, layout, playerCount, host, hostAddress.trim(), name.trim());
            }
        });
        screens.add(new MainMenuPanel(() -> layout.show(screens, PLAYERS_SCREEN)), MENU_SCREEN);
        screens.add(selection, PLAYERS_SCREEN);
        frame.setContentPane(screens);
        frame.setMinimumSize(new Dimension(900, 700));
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }

    /**
     * Modo local: todos los jugadores usan una sola ventana.
     * No utiliza DiceTestWindow ni ConsoleCardReader.
     * Con --hardware: dados y RFID desde la Pico W (TCP 5001).
     * Sin --hardware: ESPACIO para dados y tarjetas simuladas sin pedir UID.
     */
    private static void startLocal(JFrame frame, JPanel screens, CardLayout layout,
                                   int playerCount) {
        final long[] tarjetas = {57482001L, 2209842189L, 123L, 321L};
        final AtomicReference<Game> partida = new AtomicReference<>();
        final RfidSocketReader lector = new RfidSocketReader(usarHardware ? null : tarjetas, 30_000L);
        final CardReader lectorParaGame = () -> {
            // En modo sin hardware, identifica al jugador que tiene el turno.
            Game juego = partida.get();
            if (juego != null) lector.setJugadorActual(juego.getCurrentPlayerIndex());
            return lector.readCardId();
        };

        Game juego;
        try {
            juego = new Game(playerCount, lectorParaGame);
            partida.set(juego);
        } catch (RuntimeException e) {
            HalloweenDialogs.mensaje(frame, "Partida local", "No se pudo crear: " + e.getMessage());
            return;
        }
        GamePanel panel = new GamePanel(juego);
        final PuentePicoLocal[] puenteLocal = new PuentePicoLocal[1];

        if (usarHardware) {
            try {
                // Lee los mensajes reales de la Pico y aplica los dados a este panel local.
                PuentePicoLocal puente = new PuentePicoLocal(panel, lector, 5001);
                puente.iniciar();
                puenteLocal[0] = puente;
                lector.setAlEsperar(i -> System.out.println(
                        "RFID local: acerca la tarjeta del jugador " + (i + 1)));
            } catch (IOException e) {
                HalloweenDialogs.mensaje(frame, "Pico W",
                        "No se pudo iniciar el puerto 5001: " + e.getMessage());
                return;
            }
            juego.getLog().add("Modo local con Pico W. Usa el boton fisico para lanzar.");
        } else {
            // Permite probar el modo local sin Pico, sin abrir ningún botón de prueba.
            Dado dado = new Dado();
            panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke("SPACE"), "lanzar-local");
            panel.getActionMap().put("lanzar-local", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    panel.applyRoll(dado.lanzar() + dado.lanzar(), () -> { });
                }
            });
            juego.getLog().add("Modo local sin Pico: ESPACIO para lanzar; RFID simulado.");
        }

        panel.setOnGameEnd(() -> {
            if (puenteLocal[0] != null) puenteLocal[0].close();
            layout.show(screens, MENU_SCREEN);
            screens.remove(panel);
        });
        screens.add(panel, GAME_SCREEN);
        layout.show(screens, GAME_SCREEN);
    }

    private static void connectNetwork(JFrame frame, JPanel screens, CardLayout layout,
                                       int desiredPlayers, boolean hosting, String hostAddress, String name) {
        final GamePanel[] remotePanel = new GamePanel[1];
        final Cliente[] client = new Cliente[1];
        final int[] ownIndex = {-1};
        final Servidor[] serverRef = new Servidor[1];
        final PuentePicoMonopoly[] puenteRef = new PuentePicoMonopoly[1];
        final boolean[] ended = {false};
        final javax.swing.JDialog waiting = new javax.swing.JDialog(frame, "Sala de espera", false);
        final javax.swing.JLabel progress = new javax.swing.JLabel(
                "Conectando a " + hostAddress + ":5000 ...", javax.swing.SwingConstants.CENTER);
        HalloweenDialogs.estilizarEspera(waiting, progress);
        waiting.setVisible(true);

        new SwingWorker<Cliente, Void>() {
            @Override protected Cliente doInBackground() throws Exception {
                if (hosting) {
                    long[] cards = {57482001L, 2209842189L, 123L, 321L};
                    RfidSocketReader reader = new RfidSocketReader(usarHardware ? null : cards, 30_000);
                    Servidor server = new Servidor(5000, desiredPlayers, 100,
                            Game.TURN_TIME_SECONDS, reader,
                            (n, r) -> new MotorJuegoReal(new Game(n, r)));
                    serverRef[0] = server;
                    server.iniciar();
                    if (usarHardware) {
                        try {
                            PuentePicoMonopoly puente = new PuentePicoMonopoly(server, 5001);
                            puente.iniciar();
                            puenteRef[0] = puente;
                        } catch (IOException e) {
                            server.detener();
                            throw e;
                        }
                    }
                }
                Cliente connection = new Cliente(hostAddress, 5000, message ->
                        SwingUtilities.invokeLater(() -> {
                            String[] parts = message.split("\\|", -1);
                            if ("BIENVENIDO".equals(parts[0]) && parts.length > 1) {
                                ownIndex[0] = Integer.parseInt(parts[1]);
                            } else if ("LOBBY".equals(parts[0]) && parts.length > 3) {
                                progress.setText("Jugadores conectados: " + parts[1] + "/" + parts[2]
                                        + " — " + parts[3]);
                            } else if ("INICIO".equals(parts[0]) && remotePanel[0] == null) {
                                String[] names = parts[1].split(",", -1);
                                // Modelo local SOLO PARA DIBUJAR el tablero original.
                                // Este Game es SOLO visual: el servidor lleva el banco y las reglas.
                                // No debe pedir UID por consola al dibujar el tablero.
                                Game displayGame = new Game(names.length,
                                        () -> java.util.OptionalLong.empty());
                                remotePanel[0] = new GamePanel(displayGame, ownIndex[0],
                                        command -> client[0].enviar(command));
                                final GamePanel panel = remotePanel[0];
                                panel.setOnGameEnd(() -> {
                                    ended[0] = true;
                                    if (puenteRef[0] != null) {
                                        puenteRef[0].close();
                                    }
                                    if (serverRef[0] != null) {
                                        serverRef[0].detener();   // frees port 5000 (host only)
                                    }
                                    layout.show(screens, MENU_SCREEN);   // Ok on Victory -> main menu
                                    screens.remove(panel);
                                });
                                screens.add(remotePanel[0], GAME_SCREEN);
                                layout.show(screens, GAME_SCREEN);
                                waiting.dispose();
                            }
                            if ("FIN".equals(parts[0])) {
                                ended[0] = true;   // later disconnections are expected
                            }
                            if (remotePanel[0] != null) {
                                remotePanel[0].receiveNetworkMessage(message);
                            } else if ("ERROR".equals(parts[0])) {
                                waiting.dispose();
                                HalloweenDialogs.mensaje(frame, "Servidor", message);
                            }
                        }), error -> SwingUtilities.invokeLater(() -> {
                    if (ended[0]) {
                        return;
                    }
                    waiting.dispose();
                    HalloweenDialogs.mensaje(frame, "Conexión perdida", "Se perdió la conexión con el servidor.");
                }));
                client[0] = connection;
                connection.conectar(name);
                return connection;
            }
            @Override protected void done() {
                try { get(); }
                catch (Exception ex) {
                    waiting.dispose();
                    HalloweenDialogs.mensaje(frame, "Error de conexión", "No se pudo conectar: " +
                            (ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage()));
                }
            }
        }.execute();
    }
}
