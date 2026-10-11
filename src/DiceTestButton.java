import java.awt.Dimension;
import java.awt.Font;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JButton;

/**
 * ===========================================================================
 * TEMPORARY TEST CLASS - MUST BE DELETED IN THE FINAL VERSION.
 * ===========================================================================
 * Button labeled "Dice" that exists only to test turns and movement on
 * screen. In the final version the dice are handled separately (not by this
 * project), so this class and the single line in GamePanel that adds it must
 * be removed.
 *
 */
public class DiceTestButton extends JButton {

    public DiceTestButton(Consumer<Integer> onRoll) {
        super("Dice");
        Objects.requireNonNull(onRoll, "onRoll cannot be null.");

        setFont(new Font("SansSerif", Font.BOLD, 28));
        setPreferredSize(new Dimension(200, 80));
        setFocusPainted(false);

        addActionListener(e -> {
            Dado dado = new Dado();
            int total = dado.lanzar() + dado.lanzar();
            System.out.println("Dice rolled: " + total);
            onRoll.accept(total);
        });
    }
}
