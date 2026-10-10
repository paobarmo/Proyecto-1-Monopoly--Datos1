import java.awt.CardLayout;
import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Application entry point. Creates the main window (maximized, with window
 * borders; not full screen) and switches between screens using a CardLayout:
 * main menu -> player selection -> game -> (end of game) -> main menu.
 */
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
            // TODO: replace ConsoleCardReader with SerialCardReader once the
            // serial port name of the RFID reader is known.
            Game game = new Game(playerCount, new ConsoleCardReader());
            GamePanel gamePanel = new GamePanel(game);

            // TEMPORARY: separate window with the test "Dice" button. Delete
            // this block (and the two uses of diceWindow below) together with
            // the DiceTestWindow class in the final version.
            DiceTestWindow diceWindow = new DiceTestWindow(frame, gamePanel::applyRoll);
            // Esc must also open "End game?" while the focus is in the dice
            // window (clicking Dice moves the focus there).
            diceWindow.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke("ESCAPE"), "endGame");
            diceWindow.getRootPane().getActionMap()
                    .put("endGame", gamePanel.getActionMap().get("endGame"));

            // After the end-of-game window: back to the main menu.
            gamePanel.setOnExit(() -> {
                diceWindow.dispose(); // TEMPORARY (see above)
                layout.show(screens, MENU_SCREEN);
                screens.remove(gamePanel);
            });

            screens.add(gamePanel, GAME_SCREEN);
            layout.show(screens, GAME_SCREEN);
            diceWindow.setVisible(true); // TEMPORARY (see above)
        });

        screens.add(new MainMenuPanel(() -> layout.show(screens, PLAYERS_SCREEN)), MENU_SCREEN);
        screens.add(selection, PLAYERS_SCREEN);

        frame.setContentPane(screens);
        frame.setMinimumSize(new Dimension(900, 700));
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }
}
