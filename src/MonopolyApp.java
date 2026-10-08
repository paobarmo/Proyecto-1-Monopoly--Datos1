import java.awt.CardLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Application entry point. Creates the main window (maximized, with window
 * borders; not full screen) and switches between screens using a CardLayout:
 * main menu -> player selection -> game.
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
            screens.add(gamePanel, GAME_SCREEN);
            layout.show(screens, GAME_SCREEN);

            // TEMPORARY: separate window with the test "Dice" button. Delete
            // this line together with the DiceTestWindow class in the final
            // version.
            new DiceTestWindow(frame, gamePanel::applyRoll).setVisible(true);
        });

        screens.add(new MainMenuPanel(() -> layout.show(screens, PLAYERS_SCREEN)), MENU_SCREEN);
        screens.add(selection, PLAYERS_SCREEN);

        frame.setContentPane(screens);
        frame.setMinimumSize(new Dimension(900, 700));
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }
}
