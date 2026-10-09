import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;


public class CartaEventoPanel extends JPanel {

    private static final Color FONDO = new Color(255, 245, 200);
    private static final int ARCO = 30;

    public CartaEventoPanel(CartaEvento carta, Runnable onAceptar) {
        Objects.requireNonNull(carta, "carta cannot be null.");
        Objects.requireNonNull(onAceptar, "onAceptar cannot be null.");

        setOpaque(false);
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        setPreferredSize(new Dimension(340, 220));

        JLabel titulo = new JLabel("FORTUNA", SwingConstants.CENTER);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 26));

        JTextArea texto = new JTextArea(carta.getTexto());
        texto.setEditable(false);
        texto.setFocusable(false);
        texto.setOpaque(false);
        texto.setLineWrap(true);
        texto.setWrapStyleWord(true);
        texto.setFont(new Font("SansSerif", Font.PLAIN, 18));

        JButton aceptar = new JButton("Aceptar");
        aceptar.setFont(new Font("SansSerif", Font.BOLD, 18));
        aceptar.setFocusPainted(false);
        aceptar.addActionListener(e -> onAceptar.run());

        add(titulo, BorderLayout.NORTH);
        add(texto, BorderLayout.CENTER);
        add(aceptar, BorderLayout.SOUTH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth() - 4;
        int h = getHeight() - 4;

        g2.setColor(FONDO);
        g2.fillRoundRect(2, 2, w, h, ARCO, ARCO);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(2, 2, w, h, ARCO, ARCO);

        g2.dispose();
        super.paintComponent(g);
    }
}