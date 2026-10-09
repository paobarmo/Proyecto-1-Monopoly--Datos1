import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * Main game screen: gray background, on top a text saying whose turn it is
 * with the time left in the turn next to it, the board in the center, each
 * player's information in a screen corner (Player 1 top-left, Player 2
 * top-right, Player 3 bottom-left, Player 4 bottom-right), the game log on
 * the left side and the jail on the right side (apart from the board).
 *
 * Only the players taking part in the game are shown.
 *
 * Turn clock: each turn lasts Game.TURN_TIME_SECONDS. The clock is paused
 * while a move is in progress (including while waiting for a player's
 * card). If it reaches zero before the player acts, the turn is lost and
 * passes to the next player.
 */
public class GamePanel extends JPanel {

    private static final int SIDE_COLUMN_WIDTH = 300;

    // Movement animation speed: time (in milliseconds) spent on each square.
    private static final int STEP_DELAY_MS = 300;

    // The turn clock turns red when this many seconds or fewer are left.
    private static final int WARNING_SECONDS = 10;

    private final Game game;
    private final BoardPanel boardPanel;
    private final JLabel turnLabel;
    private final JLabel timerLabel;
    private final Timer turnTimer;
    private final List<PlayerInfoPanel> playerPanels = new ArrayList<>();

    private boolean animating = false;
    private int secondsLeft;

    // En red: estado solo de presentación; las reglas las ejecuta Servidor.
    private boolean networkMode;
    private int networkLocalIndex = -1;
    private int networkTurnIndex = -1;
    private boolean networkRolled;
    private boolean networkPendingPurchase;
    private boolean networkFinished;
    private Consumer<String> networkSender;
    private JButton rollButton, buyButton, skipButton, finishButton;
    private String[] networkNames;

    public GamePanel(Game game) {
        this.game = Objects.requireNonNull(game, "game cannot be null.");

        setBackground(Color.GRAY);
        setLayout(new BorderLayout());

        for (int i = 0; i < game.getPlayerCount(); i++) {
            playerPanels.add(new PlayerInfoPanel(
                    game.getPlayer(i),
                    game.getPlayerName(i),
                    PlayerColors.forPlayer(i),
                    game.getBank()));
        }

        turnLabel = new JLabel("", SwingConstants.CENTER);
        turnLabel.setFont(new Font("SansSerif", Font.BOLD, 36));
        turnLabel.setForeground(Color.BLACK);

        timerLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 36));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));
        header.add(turnLabel);
        header.add(timerLabel);

        updateTurnLabel();

        turnTimer = new Timer(1000, e -> onTimerTick());
        resetTurnTimer();

        boardPanel = new BoardPanel(game);

        add(header, BorderLayout.NORTH);
        add(boardPanel, BorderLayout.CENTER);
        add(buildLeftColumn(), BorderLayout.WEST);
        add(buildRightColumn(), BorderLayout.EAST);
    }

    /** Inicializa el mismo tablero original en modo cliente de red. */
    public GamePanel(Game game, int localIndex, Consumer<String> sender) {
        this(game);
        this.networkMode = true;
        this.networkLocalIndex = localIndex;
        this.networkSender = Objects.requireNonNull(sender);
        this.networkNames = new String[game.getPlayerCount()];
        for (int i = 0; i < networkNames.length; i++) {
            networkNames[i] = game.getPlayerName(i);
        }
        turnTimer.stop();
        timerLabel.setText("Esperando turno");
        turnLabel.setText("Esperando servidor");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        controls.setOpaque(false);
        rollButton = networkButton(controls, "Tirar dados", Protocolo.TIRAR_DADOS);
        buyButton = networkButton(controls, "Comprar", Protocolo.COMPRAR_PROPIEDAD);
        skipButton = networkButton(controls, "No comprar", Protocolo.NO_COMPRAR);
        finishButton = networkButton(controls, "Terminar turno", Protocolo.TERMINAR_TURNO);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(controls, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        updateNetworkButtons();
    }

    private JButton networkButton(JPanel holder, String text, String command) {
        JButton button = new JButton(text);
        button.addActionListener(e -> networkSender.accept(command));
        holder.add(button);
        return button;
    }

    private void updateNetworkButtons() {
        if (!networkMode) return;
        boolean myTurn = !networkFinished && networkLocalIndex == networkTurnIndex;
        rollButton.setEnabled(myTurn && !networkRolled);
        buyButton.setEnabled(myTurn && networkPendingPurchase);
        skipButton.setEnabled(myTurn && networkPendingPurchase);
        finishButton.setEnabled(myTurn && networkRolled && !networkPendingPurchase);
    }

    /** Solo se llama desde el hilo de Swing (invokeLater). */
    public void receiveNetworkMessage(String line) {
        if (!networkMode) return;
        String[] p = line.split("\\|", -1);
        try {
            switch (p[0]) {
                case "INICIO":
                    networkNames = p[1].split(",", -1);
                    game.getLog().add("Partida compartida iniciada: " + p[1]);
                    break;
                case "TURNO":
                    networkTurnIndex = Integer.parseInt(p[1]);
                    networkRolled = false;
                    networkPendingPurchase = false;
                    secondsLeft = Game.TURN_TIME_SECONDS;
                    timerLabel.setText(String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60));
                    if (!turnTimer.isRunning()) turnTimer.start();
                    turnLabel.setText("Turno de " + p[2]);
                    game.getLog().add("Turno de " + p[2]);
                    break;
                case "ESTADO":
                    networkTurnIndex = Integer.parseInt(p[1]);
                    String[] jugadores = p[2].split(";", -1);
                    int[] positions = new int[game.getPlayerCount()];
                    for (String j : jugadores) {
                        String[] d = j.split(",", -1);
                        if (d.length < 5) continue;
                        int index = Integer.parseInt(d[0]);
                        if (index < 0 || index >= positions.length) continue;
                        positions[index] = Integer.parseInt(d[3]);
                        playerPanels.get(index).setNetworkState(d[1], Integer.parseInt(d[2]), Boolean.parseBoolean(d[4]));
                    }
                    boardPanel.setNetworkPositions(positions);
                    break;
                case "DADOS":
                    networkRolled = true;
                    game.getLog().add("Dados: " + p[2] + "+" + p[3] + "; " + p[4] + " → " + p[5]);
                    break;
                case "PUEDE_COMPRAR":
                    networkPendingPurchase = true;
                    game.getLog().add("Puedes comprar " + p[1] + " por $" + p[2]);
                    break;
                case "COMPRA":
                    networkPendingPurchase = false;
                    game.getLog().add(p[2]);
                    break;
                case "OK":
                    if (p.length > 1 && "NO_COMPRAR".equals(p[1])) networkPendingPurchase = false;
                    break;
                case "FIN":
                    networkFinished = true;
                    turnTimer.stop();
                    timerLabel.setText("Fin de la partida");
                    game.getLog().add("Ganador: " + p[2]);
                    javax.swing.JOptionPane.showMessageDialog(this, "Ganador: " + p[2]);
                    break;
                case "LOG": case "EVENTO": case "ERROR":
                    game.getLog().add(p.length > 1 ? p[1] : line);
                    if ("ERROR".equals(p[0])) javax.swing.JOptionPane.showMessageDialog(this, p[1]);
                    break;
                case "ELIMINADO":
                    game.getLog().add("Eliminado: " + p[2]);
                    break;
                case "ESPERANDO_TARJETA":
                    game.getLog().add("Esperando tarjeta RFID de jugador " + (Integer.parseInt(p[1]) + 1));
                    break;
                default: break;
            }
        } catch (RuntimeException ex) {
            game.getLog().add("Mensaje de red no reconocido: " + line);
        }
        updateNetworkButtons();
        repaint();
    }

    /**
     * Repaints every player's information so it shows the current balances
     * held by the Bank. To be called after any transaction.
     */
    public void refreshBalances() {
        for (PlayerInfoPanel panel : playerPanels) {
            panel.repaint();
        }
    }

    /**
     * Applies a dice result: moves the current player square by square on
     * screen, triggering each square's reaction as the player goes through
     * it, and ends the turn when the player lands. If a reaction needs the
     * player's card, the movement waits until it is resolved. Ignored if a
     * move is already in progress.
     *
     * @param steps the dice total (number of squares to advance)
     * @param onFinished run once the move and the turn change are complete
     */
    public void applyRoll(int steps, Runnable onFinished) {
        Objects.requireNonNull(onFinished, "onFinished cannot be null.");
        if (animating) {
            return;
        }
        animating = true;

        int playerIndex = game.getCurrentPlayerIndex();
        int startPosition = game.getPlayerPosition(playerIndex);

        // The Board position changes right away; the screen is then shown
        // moving from the start square to the final one.
        MoveResult result = game.moveCurrentPlayer(steps);

        List<Casilla> path = new ArrayList<>(result.getPassedSquares());
        path.add(result.getLandingSquare());

        boardPanel.setVisualPosition(playerIndex, startPosition);
        animateStep(playerIndex, path, 0, onFinished);
    }

    /**
     * Shows one step of the move after a short pause, triggers the square's
     * reaction (off the Swing thread, since it may wait for the player's
     * card) and, when that is done, continues with the next step.
     */
    private void animateStep(int playerIndex, List<Casilla> path, int stepIndex, Runnable onFinished) {
        Timer timer = new Timer(STEP_DELAY_MS, e -> {
            Casilla square = path.get(stepIndex);
            boardPanel.setVisualPosition(playerIndex, square.getPosition());

            boolean isLanding = stepIndex == path.size() - 1;
            if (isLanding) {
                runInBackground(
                        () -> game.triggerLanded(square),
                        () -> finishMove(onFinished));
            } else {
                runInBackground(
                        () -> game.triggerPassed(square),
                        () -> animateStep(playerIndex, path, stepIndex + 1, onFinished));
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void finishMove(Runnable onFinished) {
        boardPanel.clearVisualPosition();
        game.endTurn();
        updateTurnLabel();
        refreshBalances();
        resetTurnTimer();
        animating = false;
        onFinished.run();
    }

    /**
     * Runs an action on a background thread so the window does not freeze
     * while it waits (e.g. for a card), then runs the continuation on the
     * Swing thread. If the action fails, the error is written to the log and
     * the game continues.
     */
    private void runInBackground(Runnable action, Runnable thenOnSwingThread) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    game.getLog().add("Error: " + e.getCause());
                }

                refreshBalances();
                thenOnSwingThread.run();
            }
        }.execute();
    }

    // ------------------------------------------------------------ turn clock

    private void onTimerTick() {
        if (animating) {
            return; // the clock waits while a move (or a card confirmation) is in progress
        }

        secondsLeft--;
        updateTimerLabel();

        if (secondsLeft <= 0) {
            if (networkMode) {
                turnTimer.stop(); // el servidor termina el turno, no el cliente
            } else {
                handleTurnTimeout();
            }
        }
    }

    // The player ran out of time without acting: the turn passes to the next one.
    private void handleTurnTimeout() {
        game.getLog().add("Se acabó el tiempo de "
                + game.getPlayerName(game.getCurrentPlayerIndex()) + ".");
        game.endTurn();
        updateTurnLabel();
        resetTurnTimer();
    }

    private void resetTurnTimer() {
        secondsLeft = Game.TURN_TIME_SECONDS;
        updateTimerLabel();
        turnTimer.restart();
    }

    private void updateTimerLabel() {
        timerLabel.setText(String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60));
        timerLabel.setForeground(secondsLeft <= WARNING_SECONDS ? new Color(180, 0, 0) : Color.BLACK);
    }

    private void updateTurnLabel() {
        turnLabel.setText("Player " + (game.getCurrentPlayerIndex() + 1) + " turn");
    }

    // --------------------------------------------------------------- layout

    private JPanel buildLeftColumn() {
        JPanel column = newSideColumn();
        addToColumn(column, 0, GridBagConstraints.NORTH, playerPanel(0)); // Player 1

        // Game log, in the middle: opposite to the jail.
        GridBagConstraints logConstraints = new GridBagConstraints();
        logConstraints.gridx = 0;
        logConstraints.gridy = 1;
        logConstraints.weightx = 1.0;
        logConstraints.weighty = 3.0;
        logConstraints.fill = GridBagConstraints.BOTH;
        logConstraints.insets = new Insets(15, 10, 15, 10);
        column.add(new GameLogPanel(game.getLog()), logConstraints);

        addToColumn(column, 2, GridBagConstraints.SOUTH, playerPanel(2)); // Player 3
        return column;
    }

    private JPanel buildRightColumn() {
        JPanel column = newSideColumn();
        addToColumn(column, 0, GridBagConstraints.NORTH, playerPanel(1));   // Player 2
        addToColumn(column, 1, GridBagConstraints.CENTER, new JailPanel()); // Jail
        addToColumn(column, 2, GridBagConstraints.SOUTH, playerPanel(3));   // Player 4
        return column;
    }

    private JPanel newSideColumn() {
        JPanel column = new JPanel(new GridBagLayout());
        column.setOpaque(false);
        column.setPreferredSize(new Dimension(SIDE_COLUMN_WIDTH, 0));
        return column;
    }

    private PlayerInfoPanel playerPanel(int index) {
        return index < playerPanels.size() ? playerPanels.get(index) : null;
    }

    // Rows share the column height equally. A missing player is replaced by
    // an invisible placeholder so the other rows keep their position.
    private void addToColumn(JPanel column, int row, int anchor, JComponent component) {
        JComponent content = component;
        if (content == null) {
            content = new JPanel();
            content.setOpaque(false);
        }

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = anchor;
        constraints.insets = new Insets(15, 10, 15, 10);
        column.add(content, constraints);
    }
}
