import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Objects;

import javax.swing.BorderFactory;
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
        textArea.setBackground(new Color(235, 235, 235));
        textArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        setBorder(BorderFactory.createLineBorder(Color.BLACK));
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
