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
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

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

    // Steps requested by a card during the current move (0 = none).
    private volatile int pendingExtraSteps = 0;

    // Square name requested by a card to go to directly (null = none).
    private volatile String pendingTeleport = null;

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
        game.getLog().addCardListener(this::showCard);
        game.getLog().addMoveListener(steps -> pendingExtraSteps = steps);
        game.getLog().addTeleportListener(name -> pendingTeleport = name);

        add(header, BorderLayout.NORTH);
        add(boardPanel, BorderLayout.CENTER);
        add(buildLeftColumn(), BorderLayout.WEST);
        add(buildRightColumn(), BorderLayout.EAST);
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
        startMove(() -> game.moveCurrentPlayer(steps), true, onFinished);
    }

    // Moves the current player with the given mover and animates the result.
    // Moves the current player with the given mover and animates the result.
// triggerPasses: whether squares crossed without stopping react (false when going backward).
    private void startMove(Supplier<MoveResult> mover, boolean triggerPasses, Runnable onFinished) {
        int playerIndex = game.getCurrentPlayerIndex();
        int startPosition = game.getPlayerPosition(playerIndex);

        MoveResult result = mover.get();

        List<Casilla> path = new ArrayList<>(result.getPassedSquares());
        path.add(result.getLandingSquare());

        boardPanel.setVisualPosition(playerIndex, startPosition);
        animateStep(playerIndex, path, 0, triggerPasses, onFinished);
    }

    private void animateStep(int playerIndex, List<Casilla> path, int stepIndex,
                             boolean triggerPasses, Runnable onFinished) {
        Timer timer = new Timer(STEP_DELAY_MS, e -> {
            Casilla square = path.get(stepIndex);
            boardPanel.setVisualPosition(playerIndex, square.getPosition());

            boolean isLanding = stepIndex == path.size() - 1;
            if (isLanding) {
                runInBackground(
                        () -> game.triggerLanded(square),
                        () -> afterLanding(onFinished));
            } else if (triggerPasses) {
                runInBackground(
                        () -> game.triggerPassed(square),
                        () -> animateStep(playerIndex, path, stepIndex + 1, true, onFinished));
            } else {
                animateStep(playerIndex, path, stepIndex + 1, false, onFinished);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    // After landing: if a card asked to keep moving, do it; otherwise end the turn.
    private void afterLanding(Runnable onFinished) {
        String teleport = pendingTeleport;
        int extra = pendingExtraSteps;
        pendingTeleport = null;
        pendingExtraSteps = 0;

        if (teleport != null) {
            startMove(() -> game.teleportCurrentPlayer(teleport), false, onFinished);
        } else if (extra > 0) {
            startMove(() -> game.advanceCurrentPlayer(extra), true, onFinished);
        } else if (extra < 0) {
            startMove(() -> game.retreatCurrentPlayer(-extra), false, onFinished);
        } else {
            finishMove(onFinished);
        }
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
            handleTurnTimeout();
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

    // Se llama desde el hilo de fondo del evento: muestra la carta en el hilo
// de Swing y espera a que el jugador la cierre.
    // Se llama desde el hilo de fondo del evento: dibuja la carta sobre el
// tablero (hilo de Swing) y espera a que el jugador la cierre.
    private void showCard(CartaEvento card) {
        CountDownLatch closed = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> boardPanel.showCard(card, closed::countDown));
        try {
            closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
