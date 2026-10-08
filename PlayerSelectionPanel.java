import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Player selection screen: gray background, four squares (two on top, two
 * on the bottom) and a start button.
 *
 * Players 1 and 2 are always in the game. Clicking Player 3 or Player 4
 * adds or removes them, always keeping the active players contiguous
 * (e.g. Player 4 cannot play without Player 3). The number of players is
 * therefore 2, 3 or 4.
 *
 * The start button currently does nothing unless a listener is set with
 * setOnStart; that is the hook for the next stage of the game.
 */
public class PlayerSelectionPanel extends JPanel {

    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 4;

    private final PlayerSlotPanel[] slots = new PlayerSlotPanel[MAX_PLAYERS];
    private int playerCount = MIN_PLAYERS;
    private Consumer<Integer> onStart;

    public PlayerSelectionPanel() {
        setBackground(Color.GRAY);
        setLayout(new BorderLayout());

        add(buildHintLabel(), BorderLayout.NORTH);
        add(buildSlotsArea(), BorderLayout.CENTER);
        add(buildStartArea(), BorderLayout.SOUTH);
    }

    /**
     * Sets the action executed when "Empezar" is pressed. Receives the
     * number of players selected (2, 3 or 4). While no listener is set, the
     * button has no effect.
     */
    public void setOnStart(Consumer<Integer> onStart) {
        this.onStart = onStart;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    private JLabel buildHintLabel() {
        JLabel hint = new JLabel(
                "Jugador 1 y Jugador 2 son obligatorios. Haz clic en Jugador 3 o Jugador 4 para añadirlos o quitarlos.",
                SwingConstants.CENTER);
        hint.setFont(new Font("SansSerif", Font.PLAIN, 20));
        hint.setForeground(Color.BLACK);
        hint.setBorder(BorderFactory.createEmptyBorder(30, 20, 10, 20));
        return hint;
    }

    private JPanel buildSlotsArea() {
        JPanel grid = new JPanel(new GridLayout(2, 2, 30, 30));
        grid.setOpaque(false);

        for (int i = 0; i < MAX_PLAYERS; i++) {
            final int index = i;
            slots[i] = new PlayerSlotPanel(
                    "Jugador " + (i + 1),
                    PlayerColors.forPlayer(i),
                    i < MIN_PLAYERS,
                    () -> handleSlotClick(index));
            grid.add(slots[i]);
        }

        // GridBagLayout keeps the 2x2 grid centered at its natural size, so
        // the squares stay square regardless of the window size.
        JPanel centered = new JPanel(new GridBagLayout());
        centered.setOpaque(false);
        centered.add(grid);
        return centered;
    }

    private JPanel buildStartArea() {
        JButton startButton = new JButton("Empezar");
        startButton.setFont(new Font("SansSerif", Font.BOLD, 32));
        startButton.setPreferredSize(new Dimension(260, 80));
        startButton.setFocusPainted(false);
        startButton.addActionListener(e -> {
            if (onStart != null) {
                onStart.accept(playerCount);
            }
        });

        JPanel area = new JPanel(new GridBagLayout());
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(10, 0, 40, 0));
        area.add(startButton);
        return area;
    }

    private void handleSlotClick(int index) {
        if (index < MIN_PLAYERS) {
            return; // Players 1 and 2 cannot be removed.
        }

        // Clicking an active slot removes it and every slot after it;
        // clicking an inactive slot adds it and every slot before it.
        playerCount = slots[index].isActive() ? index : index + 1;
        refreshSlots();
    }

    private void refreshSlots() {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            slots[i].setActive(i < playerCount);
        }
    }
}
