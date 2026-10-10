import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * Main game screen: gray background, on top a text saying whose turn it is
 * with the time left in the turn next to it, the board in the center, each
 * player's information in a screen corner (Player 1 top-left, Player 2
 * top-right, Player 3 bottom-left, Player 4 bottom-right), the game log on
 * the left side and the jail on the right side (apart from the board).
 *
 * Turn clock: each turn lasts Game.TURN_TIME_SECONDS. The clock is paused
 * while a move is in progress (including while waiting for a player's card
 * or a purchase answer) and while the "End game?" window is open. If it
 * reaches zero before the player acts, the turn is lost and passes to the
 * next player.
 *
 * The Esc key opens the "End game?" window. The game finishes when only one
 * player is left or when the players choose "End"; then the end-of-game
 * window is shown and, after "Ok", the exit action is run (see setOnExit).
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
    private boolean paused = false;
    private boolean stopped = false;
    private int secondsLeft;
    private Runnable onExit;

    public GamePanel(Game game) {
        this.game = Objects.requireNonNull(game, "game cannot be null.");

        setBackground(Color.GRAY);
        setLayout(new BorderLayout());

        for (int i = 0; i < game.getPlayerCount(); i++) {
            final int playerIndex = i;
            playerPanels.add(new PlayerInfoPanel(
                    game.getPlayer(i),
                    playerIndex,
                    game.getPlayerName(i),
                    PlayerColors.forPlayer(i),
                    game.getBank(),
                    () -> game.isEliminated(playerIndex)));
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

        game.setPurchasePrompt(this::askToBuy);
        registerEscapeKey();
    }

    /**
     * Sets the action to run after the end-of-game window is closed with
     * "Ok" (normally: go back to the main menu).
     */
    public void setOnExit(Runnable onExit) {
        this.onExit = onExit;
    }

    /**
     * Stops the turn clock and ignores anything still in progress. Called
     * when the game is over.
     */
    public void stop() {
        stopped = true;
        turnTimer.stop();
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
     * player's card or an answer, the movement waits until it is resolved.
     * Ignored if a move is already in progress.
     *
     * @param steps the dice total (number of squares to advance)
     * @param onFinished run once the move and the turn change are complete
     */
    public void applyRoll(int steps, Runnable onFinished) {
        Objects.requireNonNull(onFinished, "onFinished cannot be null.");
        if (animating || stopped) {
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
            if (stopped) {
                return;
            }

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
        refreshBalances();
        animating = false;

        if (game.isGameOver()) {
            finishGame();
            return;
        }

        game.endTurn();
        updateTurnLabel();
        resetTurnTimer();
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

                if (stopped) {
                    return; // the game ended while this was in progress
                }

                refreshBalances();
                thenOnSwingThread.run();
            }
        }.execute();
    }

    // ----------------------------------------------------- purchase pop-up

    /**
     * Asks the player whether to buy a property, using a pop-up window.
     * Called from a background thread (while a square's reaction runs); the
     * window is shown on the Swing thread and this method waits for the answer.
     */
    private boolean askToBuy(String playerName, CasillaPropiedad property) {
        boolean[] answer = {false};

        try {
            SwingUtilities.invokeAndWait(() ->
                    answer[0] = GameDialogs.confirmPurchase(window(), playerName, property));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Could not show the purchase window.", e.getCause());
        }

        return answer[0];
    }

    // ------------------------------------------------------- end of the game

    private void registerEscapeKey() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "endGame");
        getActionMap().put("endGame", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                askToEndGame();
            }
        });
    }

    private void askToEndGame() {
        if (stopped || paused) {
            return;
        }

        paused = true; // the turn clock waits while the window is open
        boolean end = GameDialogs.confirmEndGame(window());
        paused = false;

        if (end) {
            finishGame();
        }
    }

    // Shows the result and returns to wherever onExit leads (the main menu).
    private void finishGame() {
        stop();

        int winner = game.getWinnerIndex();
        String winnerName = winner < 0 ? null : game.getPlayerName(winner);

        if (winnerName == null) {
            game.getLog().add("La partida termina en empate.");
        } else {
            game.getLog().add("¡" + winnerName + " gana la partida!");
        }

        GameDialogs.showGameOver(window(), winnerName);

        if (onExit != null) {
            onExit.run();
        }
    }

    private Window window() {
        return SwingUtilities.getWindowAncestor(this);
    }

    // ------------------------------------------------------------ turn clock

    private void onTimerTick() {
        if (animating || paused || stopped) {
            return; // the clock waits while a move or a window is in progress
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

    // The background image (if any) is drawn centered over the gray, behind
    // everything else.
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Decoration.paintBackground(g, getWidth(), getHeight());
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
