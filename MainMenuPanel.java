import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.BasicStroke;
import java.awt.geom.RoundRectangle2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.io.File;
import java.net.URL;
import javax.imageio.ImageIO;
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

    private static final Color TITLE_COLOR = new Color(255, 222, 157);
    private final Image portada = cargarPortada();

    private static Image cargarPortada() {
        try {
            URL recurso = MainMenuPanel.class.getResource("/images/portada.jpg");
            if (recurso != null) return ImageIO.read(recurso);
            // Alternativa para proyectos IntelliJ con los .java dentro de src.
            File archivo = new File("src/images/portada.jpg");
            if (!archivo.exists()) archivo = new File("images/portada.jpg");
            return archivo.exists() ? ImageIO.read(archivo) : null;
        } catch (Exception e) {
            System.err.println("No se pudo cargar la portada: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (portada == null || getWidth() <= 0 || getHeight() <= 0) return;
        int iw = portada.getWidth(this);
        int ih = portada.getHeight(this);
        if (iw <= 0 || ih <= 0) return;
        // Escala para cubrir la ventana, recortando el excedente sin deformar.
        double escala = Math.max((double) getWidth() / iw, (double) getHeight() / ih);
        int ancho = (int) Math.ceil(iw * escala);
        int alto = (int) Math.ceil(ih * escala);
        int x = (getWidth() - ancho) / 2;
        int y = (getHeight() - alto) / 2;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(portada, x, y, ancho, alto, this);
        g2.dispose();
    }

    public MainMenuPanel(Runnable onStart) { this(onStart, null); }

    public MainMenuPanel(Runnable onStart, Runnable onNetwork) {
        Objects.requireNonNull(onStart, "onStart cannot be null.");

        setBackground(Color.GRAY);
        setLayout(new GridBagLayout());

        // El título conserva su texto y posición: solo cambia su estilo visual.
        JLabel title = new JLabel("Monopoly Halloween") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setFont(getFont());
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(new Color(22, 10, 29, 220));
                g2.drawString(getText(), x + 4, y + 5);  // sombra sutil
                g2.setColor(TITLE_COLOR);
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        title.setFont(new Font("Georgia", Font.BOLD, 70));
        title.setForeground(TITLE_COLOR);
        title.setPreferredSize(new Dimension(810, 110));

        JButton startButton = new JButton("Iniciar") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(69, 35, 67),
                        0, h, new Color(30, 20, 47)));
                g2.fill(new RoundRectangle2D.Double(1, 1, w - 3, h - 3, 24, 24));
                g2.setColor(new Color(218, 169, 100));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Double(2, 2, w - 5, h - 5, 24, 24));
                super.paintComponent(g);
                g2.dispose();
            }
        };
        startButton.setFont(new Font("Georgia", Font.BOLD, 31));
        startButton.setForeground(new Color(255, 232, 185));
        startButton.setPreferredSize(new Dimension(250, 76));
        startButton.setContentAreaFilled(false);
        startButton.setBorderPainted(false);
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
        if (onNetwork != null) {
            JButton networkButton = new JButton("Jugar por red (puerto 5000)");
            networkButton.setFont(new Font("SansSerif", Font.BOLD, 24));
            networkButton.addActionListener(e -> onNetwork.run());
            constraints.gridy = 2;
            constraints.insets = new Insets(20, 0, 0, 0);
            add(networkButton, constraints);
        }
    }
}
