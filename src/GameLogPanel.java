import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Read-only text window that shows the GameLog. It displays the messages
 * already in the log, appends every new one as it arrives and keeps the
 * view scrolled to the latest message.
 */
public class GameLogPanel extends JScrollPane {

    private final JTextArea textArea;

    public GameLogPanel(GameLog log) {
        this(log, new JTextArea());
    }

    private GameLogPanel(GameLog log, JTextArea textArea) {
        super(textArea);
        Objects.requireNonNull(log, "log cannot be null.");
        this.textArea = textArea;

        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font("SansSerif", Font.PLAIN, 16));
        textArea.setForeground(new Color(248, 226, 197));
        textArea.setCaretColor(new Color(248, 226, 197));
        textArea.setBackground(new Color(45, 34, 55));
        getViewport().setBackground(new Color(45, 34, 55));
        textArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(222, 180, 116), 2),
                "  HISTORIAL  ",
                javax.swing.border.TitledBorder.LEADING,
                javax.swing.border.TitledBorder.TOP,
                new Font("Georgia", Font.BOLD, 17), new Color(222, 180, 116)));
        setPreferredSize(new Dimension(280, 320));

        for (String message : log.getMessages()) {
            appendMessage(message);
        }
        // Messages can arrive from other threads; the text area must only be
        // changed on the Swing event thread.
        log.addListener(message -> SwingUtilities.invokeLater(() -> appendMessage(message)));
    }

    private void appendMessage(String message) {
        textArea.append(message + "\n");
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }
}
