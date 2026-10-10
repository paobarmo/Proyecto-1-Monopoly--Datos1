import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

/**
 * Pop-up windows of the game. Each method shows a modal window (the rest of
 * the game cannot be used while it is open) and returns when it is closed.
 * They must be called from the Swing event thread.
 */
public final class GameDialogs {

    private static final Color BACKGROUND = Color.GRAY;
    private static final Color TITLE_COLOR = new Color(255, 140, 0);

    private GameDialogs() {
        // Utility class: not meant to be instantiated.
    }

    /**
     * Asks the player whether to buy a property.
     *
     * @return true if the player chose "Comprar", false if "Rechazar"
     */
    public static boolean confirmPurchase(Window owner, String playerName, CasillaPropiedad property) {
        JDialog dialog = newDialog(owner, "Comprar propiedad");
        boolean[] buy = {false};

        JPanel content = newContent();
        content.add(label(playerName + ", ¿desea comprar esta propiedad?", 22, false));
        content.add(Box.createVerticalStrut(15));
        content.add(label(property.getName(), 40, true));
        content.add(Box.createVerticalStrut(15));
        content.add(label("Grupo: " + property.getColorGroup(), 22, false));
        content.add(label("Precio: " + property.getPrice(), 22, false));
        content.add(label("Renta: " + property.getRentWithoutGroup() + " ("
                + property.getRentWithGroup() + " con el grupo completo)", 22, false));
        content.add(Box.createVerticalStrut(25));
        content.add(buttonRow(
                button("Comprar", () -> {
                    buy[0] = true;
                    dialog.dispose();
                }),
                button("Rechazar", dialog::dispose)));

        show(dialog, content, owner);
        return buy[0];
    }

    /**
     * Asks whether to end the game. Closing the window or pressing Esc again
     * is the same as choosing "Return".
     *
     * @return true if the player chose "End", false if "Return"
     */
    public static boolean confirmEndGame(Window owner) {
        JDialog dialog = newDialog(owner, "End game");
        boolean[] end = {false};

        JPanel content = newContent();
        content.add(label("End game?", 48, true));
        content.add(Box.createVerticalStrut(30));
        content.add(buttonRow(
                button("Return", dialog::dispose),
                button("End", () -> {
                    end[0] = true;
                    dialog.dispose();
                })));

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dialog.dispose();
            }
        });
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        show(dialog, content, owner);
        return end[0];
    }

    /**
     * Shows the end of the game: "Victory" with the winner's name, or "Draw"
     * when there is no single winner.
     *
     * @param winnerName the winner's name, or null for a draw
     */
    public static void showGameOver(Window owner, String winnerName) {
        boolean draw = winnerName == null;
        JDialog dialog = newDialog(owner, draw ? "Draw" : "Victory");

        JPanel content = newContent();
        JLabel title = label(draw ? "Draw" : "Victory", 72, true);
        title.setForeground(TITLE_COLOR);
        content.add(title);

        if (!draw) {
            content.add(Box.createVerticalStrut(15));
            content.add(label(winnerName, 40, false));
        }

        content.add(Box.createVerticalStrut(35));
        content.add(buttonRow(button("Ok", dialog::dispose)));

        show(dialog, content, owner);
    }

    // --------------------------------------------------------------- helpers

    private static JDialog newDialog(Window owner, String title) {
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);
        return dialog;
    }

    private static JPanel newContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));
        return content;
    }

    private static JLabel label(String text, int size, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(Color.BLACK);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 24));
        button.setPreferredSize(new Dimension(170, 60));
        button.setFocusPainted(false);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static JPanel buttonRow(JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (JButton button : buttons) {
            row.add(button);
        }
        return row;
    }

    // Shows the dialog and waits until it is closed (it is modal).
    private static void show(JDialog dialog, JPanel content, Window owner) {
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
