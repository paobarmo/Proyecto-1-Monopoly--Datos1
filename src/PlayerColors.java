import java.awt.Color;

/**
 * Single place defining the color assigned to each player (index 0 = Player
 * 1), shared by every screen so a player always has the same color.
 */
public final class PlayerColors {

    private static final Color[] COLORS = {
            new Color(255, 140, 0),   // orange
            new Color(138, 43, 226),  // purple
            new Color(50, 205, 50),   // green
            new Color(220, 20, 60)    // red
    };

    private PlayerColors() {
        // Utility class: not meant to be instantiated.
    }

    public static Color forPlayer(int index) {
        return COLORS[index];
    }
}
