import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import java.awt.Image;
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
        setBackground(new Color(32, 25, 42));
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

    public int getAvatar(int playerIndex) { return AvatarSprites.choice(playerIndex); }

    public int getPlayerCount() {
        return playerCount;
    }

    private JLabel buildHintLabel() {
        JLabel hint = new JLabel("<html><div style='text-align:center;'>"
                + "<span style='font-size:28px;'>ELIGE TUS PERSONAJES</span><br>"
                + "Jugador 1 y 2 son obligatorios · Haz clic en Jugador 3 o 4 para añadirlos"
                + "</div></html>", SwingConstants.CENTER);
        hint.setFont(new Font("Georgia", Font.PLAIN, 17));
        hint.setForeground(new Color(237, 211, 164));
        hint.setBorder(BorderFactory.createEmptyBorder(22, 16, 12, 16));
        return hint;
    }

    private JPanel buildSlotsArea() {
        JPanel grid = new JPanel(new GridLayout(2, 2, 24, 24));
        grid.setOpaque(false);

        for (int i = 0; i < MAX_PLAYERS; i++) {
            final int index = i;
            slots[i] = new PlayerSlotPanel(
                    "Jugador " + (i + 1),
                    PlayerColors.forPlayer(i),
                    i < MIN_PLAYERS,
                    () -> handleSlotClick(index), index);
            JPanel wrapped = new JPanel(new BorderLayout());
            wrapped.setOpaque(false);
            wrapped.add(slots[i], BorderLayout.CENTER);
            JButton avatarButton = new ThemeButton("");
            avatarButton.setPreferredSize(new Dimension(240, 53));
            avatarButton.setFont(new Font("Georgia", Font.BOLD, 16));
            avatarButton.addActionListener(e -> chooseAvatar(index, avatarButton));
            updateAvatarButton(index, avatarButton);
            wrapped.add(avatarButton, BorderLayout.SOUTH);
            grid.add(wrapped);
        }

        // GridBagLayout keeps the 2x2 grid centered at its natural size, so
        // the squares stay square regardless of the window size.
        JPanel centered = new JPanel(new GridBagLayout());
        centered.setOpaque(false);
        centered.add(grid);
        return centered;
    }

    private JPanel buildStartArea() {
        JButton startButton = new ThemeButton("Empezar");
        startButton.setFont(new Font("Georgia", Font.BOLD, 30));
        startButton.setPreferredSize(new Dimension(260, 80));
        startButton.setFocusPainted(false);
        startButton.addActionListener(e -> {
            if (onStart != null) {
                onStart.accept(playerCount);
            }
        });

        JPanel area = new JPanel(new GridBagLayout());
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(12, 0, 22, 0));
        area.add(startButton);
        return area;
    }

    private void updateAvatarButton(int player, JButton button) {
        int avatar = AvatarSprites.choice(player);
        button.setText(AvatarSprites.name(avatar) + "  ▾");
        Image image = AvatarSprites.forAvatar(avatar);
        button.setIcon(image == null ? null : new ImageIcon(image.getScaledInstance(37, 37, Image.SCALE_FAST)));
    }

    private void chooseAvatar(int player, JButton button) {
        int selected = HalloweenDialogs.avatar(this, player);
        if (selected >= 0) {
            AvatarSprites.select(player, selected);
            updateAvatarButton(player, button);
            slots[player].repaint();
        }
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
    /** Estilo visual: no altera eventos ni selección de jugadores. */
    private static class ThemeButton extends JButton {
        ThemeButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setForeground(new Color(250, 226, 187));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getModel().isRollover() ? new Color(100, 62, 95) : new Color(68, 44, 75));
            g2.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
            g2.setColor(new Color(197, 155, 101));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
            g2.dispose();
            super.paintComponent(g);
        }
    }

}
