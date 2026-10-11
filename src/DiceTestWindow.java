import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.Objects;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.WindowConstants;

/**
 * ===========================================================================
 * TEMPORARY TEST CLASS - MUST BE DELETED IN THE FINAL VERSION.
 * ===========================================================================
 * Small separate window with a "Dice" button, used only to test turns and
 * movement without taking space in the game screen. In the final version
 * the dice are handled separately (not by this project), so this class and
 * the block in MonopolyApp that creates it must be removed.
 *
 * The button is disabled while the
 * move is being shown on screen, and enabled again when it finishes.
 *
 * The window is not modal (the game can still be clicked) and cannot be
 * closed on its own, so the dice are not lost by accident; it closes with
 * the main window.
 */
public class DiceTestWindow extends JDialog {

    private final Dado dado = new Dado();

    /**
     * @param owner the main window this one belongs to (it stays above it)
     * @param onRoll receives the dice total and a Runnable that must be run
     *               when the move has finished, to enable the button again
     */
    public DiceTestWindow(Window owner, BiConsumer<Integer, Runnable> onRoll) {
        super(owner, "Dice (test)", Dialog.ModalityType.MODELESS);
        Objects.requireNonNull(onRoll, "onRoll cannot be null.");

        JButton diceButton = new JButton("Dice");
        diceButton.setFont(new Font("SansSerif", Font.BOLD, 28));
        diceButton.setPreferredSize(new Dimension(180, 70));
        diceButton.setFocusPainted(false);
        diceButton.addActionListener(e -> {
            int total = dado.lanzar() + dado.lanzar();
            diceButton.setEnabled(false);
            onRoll.accept(total, () -> diceButton.setEnabled(true));
        });

        setLayout(new FlowLayout(FlowLayout.CENTER, 20, 20));
        add(diceButton);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner); // center of the screen: the empty middle of the board
    }
}
