import java.awt.CardLayout;
import java.awt.Dimension;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class MonopolyApp {
    private static final String MENU_SCREEN = "MENU";
    private static final String PLAYERS_SCREEN = "PLAYERS";
    private static final String GAME_SCREEN = "GAME";

    public static void main(String[] args) {
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
                // Se conserva literalmente el comportamiento anterior.
                Game game = new Game(playerCount, new ConsoleCardReader());
                GamePanel gamePanel = new GamePanel(game);
                screens.add(gamePanel, GAME_SCREEN);
                layout.show(screens, GAME_SCREEN);
                new DiceTestWindow(frame, gamePanel::applyRoll).setVisible(true);
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

    private static void connectNetwork(JFrame frame, JPanel screens, CardLayout layout,
                                       int desiredPlayers, boolean hosting, String hostAddress, String name) {
        final GamePanel[] remotePanel = new GamePanel[1];
        final Cliente[] client = new Cliente[1];
        final int[] ownIndex = {-1};
        final javax.swing.JDialog waiting = new javax.swing.JDialog(frame, "Sala de espera", false);
        final javax.swing.JLabel progress = new javax.swing.JLabel(
                "Conectando a " + hostAddress + ":5000 ...", javax.swing.SwingConstants.CENTER);
        HalloweenDialogs.estilizarEspera(waiting, progress);
        waiting.setVisible(true);

        new SwingWorker<Cliente, Void>() {
            @Override protected Cliente doInBackground() throws Exception {
                if (hosting) {
                    long[] cards = {57482001L, 2209842189L, 123L, 321L};
                    RfidSocketReader reader = new RfidSocketReader(cards, 30_000);
                    Servidor server = new Servidor(5000, desiredPlayers, 100,
                            Game.TURN_TIME_SECONDS, reader,
                            (n, r) -> new MotorJuegoReal(new Game(n, r)));
                    server.iniciar();
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
                                Game displayGame = new Game(names.length, new ConsoleCardReader());
                                remotePanel[0] = new GamePanel(displayGame, ownIndex[0],
                                        command -> client[0].enviar(command));
                                screens.add(remotePanel[0], GAME_SCREEN);
                                layout.show(screens, GAME_SCREEN);
                                waiting.dispose();
                            }
                            if (remotePanel[0] != null) {
                                remotePanel[0].receiveNetworkMessage(message);
                            } else if ("ERROR".equals(parts[0])) {
                                waiting.dispose();
                                HalloweenDialogs.mensaje(frame, "Servidor", message);
                            }
                        }), error -> SwingUtilities.invokeLater(() -> {
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
