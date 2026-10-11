import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
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
    private static final Color GAME_BACKGROUND = new Color(31, 23, 42);
    private static final Color CARD_BACKGROUND = new Color(51, 37, 61);
    private static final Color GOLD = new Color(222, 180, 116);
    private static final Color CREAM = new Color(248, 226, 197);

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
    private int[] networkPositions;
    private int networkAnimationPlayer = -1;
    private int networkAnimationFrom;
    private int networkAnimationTo;
    private Timer networkStepTimer;
    private String[] networkNames;
    private int[] networkJailTurns;
    private JailPanel jailPanel;

    // Fin de partida, pausa (ESC) y jugadores eliminados.
    private boolean gameEnded = false;
    private boolean pauseOpen = false;
    private Runnable onGameEnd = () -> { };
    private KeyEventDispatcher escDispatcher;
    private boolean[] networkActive;
    private final java.util.Set<Integer> removedPanels = new java.util.HashSet<>();

    public GamePanel(Game game) {
        this.game = Objects.requireNonNull(game, "game cannot be null.");

        setBackground(GAME_BACKGROUND);
        setLayout(new BorderLayout());

        for (int i = 0; i < game.getPlayerCount(); i++) {
            PlayerInfoPanel playerInfo = new PlayerInfoPanel(
                    game.getPlayer(i),
                    game.getPlayerName(i),
                    PlayerColors.forPlayer(i),
                    game.getBank());
            playerInfo.setAvatarPlayerIndex(i);
            playerPanels.add(playerInfo);
        }

        turnLabel = new JLabel("", SwingConstants.CENTER);
        turnLabel.setFont(new Font("Georgia", Font.BOLD, 31));
        turnLabel.setForeground(GOLD);

        timerLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Georgia", Font.BOLD, 30));
        timerLabel.setForeground(CREAM);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0));
        header.setOpaque(true);
        header.setBackground(CARD_BACKGROUND);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, GOLD),
                BorderFactory.createEmptyBorder(15, 10, 13, 10)));

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
        // Los controles de red se manejan por teclado; no se agregan botones al diseño.
        registerNetworkKey("SPACE", "tirar", Protocolo.TIRAR_DADOS);
        registerNetworkKey("C", "comprar", Protocolo.COMPRAR_PROPIEDAD);
        registerNetworkKey("N", "rechazar", Protocolo.NO_COMPRAR);
        registerNetworkKey("F", "terminar", Protocolo.TERMINAR_TURNO);
        game.getLog().add("Red: ESPACIO tirar dados; C comprar; N no comprar; F terminar turno; ESC pausa.");
    }

    private void registerNetworkKey(String key, String actionName, String command) {
        String name = "red_" + actionName;
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (canSendNetworkCommand(command)) networkSender.accept(command);
            }
        });
    }

    private boolean canSendNetworkCommand(String command) {
        if (!networkMode || networkFinished || networkLocalIndex != networkTurnIndex) return false;
        if (networkAnimationPlayer >= 0) return false;
        switch (command) {
            case Protocolo.TIRAR_DADOS: return !networkRolled;
            case Protocolo.COMPRAR_PROPIEDAD:
            case Protocolo.NO_COMPRAR: return networkPendingPurchase;
            case Protocolo.TERMINAR_TURNO: return networkRolled && !networkPendingPurchase;
            default: return false;
        }
    }

    // Anima únicamente la ficha. El servidor ya ejecutó las reglas del movimiento.
    private void animateNetworkRoll(int player, int from, int to) {
        if (networkStepTimer != null) networkStepTimer.stop();
        networkAnimationPlayer = player;
        networkAnimationFrom = from;
        networkAnimationTo = to;
        final int squares = game.getBoard().getSize();
        final int steps = (to - from + squares) % squares;
        final int[] progress = {0};
        boardPanel.setVisualPosition(player, from);
        networkStepTimer = new Timer(STEP_DELAY_MS, e -> {
            progress[0]++;
            boardPanel.setVisualPosition(player, (from + progress[0]) % squares);
            if (progress[0] >= steps) {
                networkStepTimer.stop();
                networkAnimationPlayer = -1;
                boardPanel.clearVisualPosition();
                refreshJail();
            }
        });
        networkStepTimer.setRepeats(true);
        if (steps == 0) {
            networkAnimationPlayer = -1;
            boardPanel.clearVisualPosition();
            refreshJail();
        } else {
            networkStepTimer.start();
        }
    }

    /** Solo se llama desde el hilo de Swing (invokeLater). */
    public void receiveNetworkMessage(String line) {
        if (!networkMode) return;
        String[] p = line.split("\\|", -1);
        try {
            switch (p[0]) {
                case "AVATAR":
                    if (p.length >= 3) {
                        AvatarSprites.select(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                        boardPanel.repaint();
                        refreshBalances();
                    }
                    break;
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
                    int[] jail = new int[game.getPlayerCount()];
                    boolean[] active = new boolean[game.getPlayerCount()];
                    java.util.Arrays.fill(active, true);
                    for (String j : jugadores) {
                        String[] d = j.split(",", -1);
                        if (d.length < 5) continue;
                        int index = Integer.parseInt(d[0]);
                        if (index < 0 || index >= positions.length) continue;
                        positions[index] = Integer.parseInt(d[3]);
                        if (d.length >= 6) jail[index] = Integer.parseInt(d[5]);
                        active[index] = Boolean.parseBoolean(d[4]);
                        playerPanels.get(index).setNetworkState(d[1], Integer.parseInt(d[2]), Boolean.parseBoolean(d[4]));
                    }
                    networkActive = active;
                    boardPanel.setNetworkActive(active);
                    syncEliminatedPanels();
                    networkPositions = positions;
                    boardPanel.setNetworkPositions(positions);
                    networkJailTurns = jail;
                    boardPanel.setNetworkJailTurns(jail);
                    refreshJail();
                    if (p.length >= 4) {
                        boardPanel.setNetworkOwners(parseNetworkOwners(p[3]));
                    }
                    break;
                case "DADOS":
                    networkRolled = true;
                    animateNetworkRoll(Integer.parseInt(p[1]), Integer.parseInt(p[4]), Integer.parseInt(p[5]));
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
                    gameEnded = true;
                    turnTimer.stop();
                    timerLabel.setText("Fin de la partida");
                    String winnerName = "-1".equals(p[1]) ? "Nadie" : p[2];
                    game.getLog().add("Ganador: " + winnerName);
                    closePauseDialogs();
                    repaint();
                    HalloweenDialogs.victoria(this, winnerName);
                    onGameEnd.run();
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
        repaint();
    }

    // "posicion:indice,posicion:indice" -> dueño de cada casilla (-1 si no tiene).
    private int[] parseNetworkOwners(String data) {
        int[] owners = new int[game.getBoard().getSize()];
        java.util.Arrays.fill(owners, -1);
        if (data == null || data.isBlank()) {
            return owners;
        }
        for (String entry : data.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length != 2) {
                continue;
            }
            int square = Integer.parseInt(parts[0].trim());
            int owner = Integer.parseInt(parts[1].trim());
            if (square >= 0 && square < owners.length) {
                owners[square] = owner;
            }
        }
        return owners;
    }

    /**
     * Repaints every player's information so it shows the current balances
     * held by the Bank, and the board so it shows the property owners.
     * To be called after any transaction.
     */
    public void refreshBalances() {
        syncEliminatedPanels();
        for (PlayerInfoPanel panel : playerPanels) {
            panel.repaint();
        }
        boardPanel.repaint();
        refreshJail();
    }

    /**
     * Shows in the jail the players that are in it. In a network game, the
     * player whose token is still walking to the Go to Jail square is shown
     * in the jail only once the animation ends.
     */
    private void refreshJail() {
        int count = game.getPlayerCount();
        int[] shown = new int[count];
        for (int i = 0; i < count; i++) {
            int turns = networkMode && networkJailTurns != null
                    ? networkJailTurns[i]
                    : game.getJailTurnsLeft(i);
            shown[i] = networkMode && i == networkAnimationPlayer ? 0 : turns;
        }
        jailPanel.setJailTurns(shown);
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
        if (animating || gameEnded) {
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
            if (gameEnded) {
                return;
            }
            Casilla square = path.get(stepIndex);
            boardPanel.setVisualPosition(playerIndex, square.getPosition());

            boolean isLanding = stepIndex == path.size() - 1;
            if (isLanding) {
                runInBackground(
                        () -> game.triggerLanded(square),
                        () -> {
                            game.eliminateBankruptPlayers();
                            if (game.isEliminated(playerIndex) || game.isGameOver()) {
                                finishMove(onFinished);   // eliminated: no purchase is offered
                            } else {
                                offerPurchase(() -> finishMove(onFinished));
                            }
                        });
            } else {
                runInBackground(
                        () -> game.triggerPassed(square),
                        () -> animateStep(playerIndex, path, stepIndex + 1, onFinished));
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * After landing (local game): if the square is a property with no owner,
     * asks the current player whether to buy it. The payment asks for the
     * player's card, so it runs in the background. If the payment is not
     * confirmed, the player is asked again. Always ends by running "next" on
     * the Swing thread.
     */
    private void offerPurchase(Runnable next) {
        CasillaPropiedad property = game.getBuyableProperty();
        if (property == null) {
            next.run();
            return;
        }

        Player buyer = game.getCurrentPlayer();
        String buyerName = game.getPlayerName(game.getCurrentPlayerIndex());
        int price = property.getPrice();
        int balance = game.getBank().getBalance(buyer);

        if (balance < price) {
            game.getLog().add(buyerName + " no tiene saldo para comprar " + property.getName()
                    + " (cuesta " + price + ").");
            next.run();
            return;
        }

        int answer = javax.swing.JOptionPane.showConfirmDialog(this,
                buyerName + ", ¿quieres comprar " + property.getName() + " por $" + price + "?\n"
                        + "Tu saldo: $" + balance,
                "Comprar propiedad", javax.swing.JOptionPane.YES_NO_OPTION);

        if (answer != javax.swing.JOptionPane.YES_OPTION) {
            game.getLog().add(buyerName + " decidió no comprar " + property.getName() + ".");
            next.run();
            return;
        }

        game.getLog().add("Confirme su transacción.");
        runInBackground(() -> {
            try {
                game.buyCurrentProperty();
            } catch (ErrorJuego e) {
                game.getLog().add(e.getMessage());
            }
        }, () -> {
            if (game.getBuyableProperty() == null) {
                next.run();            // comprada
            } else {
                offerPurchase(next);   // el pago no se confirmó: preguntar de nuevo
            }
        });
    }

    private void finishMove(Runnable onFinished) {
        if (gameEnded) {
            return;
        }
        boardPanel.clearVisualPosition();
        game.eliminateBankruptPlayers();   // balance 0 after buying, paying taxes, etc.
        refreshBalances();

        if (game.isGameOver()) {
            int winner = game.getWinnerIndex();
            showVictory(winner >= 0 ? java.util.Collections.singletonList(winner)
                    : java.util.Collections.<Integer>emptyList());
            return;
        }

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

                if (gameEnded) {
                    return;
                }
                refreshBalances();
                thenOnSwingThread.run();
            }
        }.execute();
    }

    // ------------------------------------------------ ESC, victory, eliminations

    /** What to do after the player presses Ok on the Victory screen (go back to the menu). */
    public void setOnGameEnd(Runnable onGameEnd) {
        this.onGameEnd = Objects.requireNonNull(onGameEnd, "onGameEnd cannot be null.");
    }

    // ESC is listened to in the whole application (also when the focus is in the dice
    // window), except when a modal dialog (purchase, pause...) is already open.
    @Override
    public void addNotify() {
        super.addNotify();
        if (escDispatcher == null) {
            escDispatcher = this::onKeyEvent;
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escDispatcher);
        }
    }

    @Override
    public void removeNotify() {
        if (escDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escDispatcher);
            escDispatcher = null;
        }
        super.removeNotify();
    }

    private boolean onKeyEvent(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED || e.getKeyCode() != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (gameEnded || pauseOpen || !isShowing()) {
            return false;
        }
        if (networkMode && (networkFinished || networkSender == null || isGone(networkLocalIndex))) {
            return false;
        }
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active instanceof Dialog && ((Dialog) active).isModal()) {
            return false;
        }
        pauseOpen = true;
        SwingUtilities.invokeLater(this::openPauseMenu);
        return false;
    }

    private void openPauseMenu() {
        if (!networkMode) {
            turnTimer.stop();   // in a network game the server owns the clock
        }
        int choice = -1;
        try {
            choice = HalloweenDialogs.pausa(this, networkMode);
        } finally {
            pauseOpen = false;
        }
        if (gameEnded) {
            return;
        }
        if (choice == 1) {
            if (networkMode) {
                networkSender.accept(Protocolo.TERMINAR_PARTIDA);   // the server answers with FIN
            } else {
                game.getLog().add("Partida terminada antes de tiempo.");
                showVictory(game.getRichestPlayerIndexes());
                return;
            }
        }
        if (!networkMode) {
            turnTimer.start();
        }
    }

    // Closes the pause dialog if it is open (e.g. the game ended while it was showing).
    private void closePauseDialogs() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog && window.isVisible()
                    && "Pausa".equals(((JDialog) window).getTitle())) {
                window.dispose();
            }
        }
    }

    /** Shows Victory (local game) and, on Ok, goes back to the main menu. */
    private void showVictory(List<Integer> winners) {
        if (gameEnded) {
            return;
        }
        gameEnded = true;
        turnTimer.stop();
        closePauseDialogs();

        StringBuilder name = new StringBuilder();
        for (int winner : winners) {
            if (name.length() > 0) {
                name.append(" y ");
            }
            name.append(game.getPlayerName(winner));
        }
        if (name.length() == 0) {
            name.append("Nadie");
        }

        game.getLog().add("Victory: " + name);
        HalloweenDialogs.victoria(this, name.toString());
        onGameEnd.run();
    }

    private boolean isGone(int index) {
        if (networkMode) {
            return networkActive != null && index >= 0 && index < networkActive.length
                    && !networkActive[index];
        }
        return game.isEliminated(index);
    }

    // Removes an eliminated player's information from the side column, leaving an
    // empty space so the other panels do not change place.
    private void syncEliminatedPanels() {
        for (int i = 0; i < playerPanels.size(); i++) {
            if (!isGone(i) || removedPanels.contains(i)) {
                continue;
            }
            PlayerInfoPanel panel = playerPanels.get(i);
            Container parent = panel.getParent();
            if (parent == null || !(parent.getLayout() instanceof GridBagLayout)) {
                continue;
            }
            GridBagConstraints constraints = ((GridBagLayout) parent.getLayout()).getConstraints(panel);
            JPanel spacer = new JPanel();
            spacer.setOpaque(false);
            spacer.setPreferredSize(panel.getPreferredSize());
            parent.remove(panel);
            parent.add(spacer, constraints);
            parent.revalidate();
            parent.repaint();
            removedPanels.add(i);
        }
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
        refreshJail();
        resetTurnTimer();
    }

    private void resetTurnTimer() {
        secondsLeft = Game.TURN_TIME_SECONDS;
        updateTimerLabel();
        if (pauseOpen || gameEnded) {
            turnTimer.stop();
        } else {
            turnTimer.restart();
        }
    }

    private void updateTimerLabel() {
        timerLabel.setText(String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60));
        timerLabel.setForeground(secondsLeft <= WARNING_SECONDS ? new Color(255, 140, 134) : CREAM);
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
        jailPanel = new JailPanel();
        JailPanel jail = jailPanel;
        jail.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD, 2),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        addToColumn(column, 1, GridBagConstraints.CENTER, jail); // Jail
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
