import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Main menu screen: gray background, large title and a start button.
 * The action performed when the button is pressed is provided by the
 * caller, so this panel does not need to know about screen navigation.
 */
public class MainMenuPanel extends JPanel {

    private static final Color TITLE_COLOR = new Color(255, 140, 0);

    public MainMenuPanel(Runnable onStart) {
        this(onStart, () -> {});
    }

    public MainMenuPanel(Runnable onStart, Runnable onNetworkTest) {
        Objects.requireNonNull(onStart, "onStart cannot be null.");
        Objects.requireNonNull(onNetworkTest, "onNetworkTest cannot be null.");

        setBackground(Color.GRAY);
        setLayout(new GridBagLayout());

        JLabel title = new JLabel("Monopoly Halloween");
        title.setFont(new Font("SansSerif", Font.BOLD, 96));
        title.setForeground(TITLE_COLOR);

        JButton startButton = new JButton("Iniciar");
        startButton.setFont(new Font("SansSerif", Font.BOLD, 36));
        startButton.setPreferredSize(new Dimension(280, 90));
        startButton.setFocusPainted(false);
        startButton.addActionListener(e -> onStart.run());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets = new Insets(0, 0, 80, 0);
        add(title, constraints);

        constraints.gridy = 1;
        constraints.insets = new Insets(0, 0, 0, 0);
        add(startButton, constraints);

        JButton networkButton = new JButton("Probar conexión de red");
        networkButton.setFont(new Font("SansSerif", Font.BOLD, 22));
        networkButton.addActionListener(e -> onNetworkTest.run());
        constraints.gridy = 2;
        constraints.insets = new Insets(25, 0, 0, 0);
        add(networkButton, constraints);
    }
}
