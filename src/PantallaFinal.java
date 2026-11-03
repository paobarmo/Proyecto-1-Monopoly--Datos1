import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class PantallaFinal extends JDialog {

    public PantallaFinal(Window owner, String ganador, String[][] resultados, Runnable onClose) {
        super(owner, "Juego finalizado", ModalityType.APPLICATION_MODAL);

        Color fondo = new Color(31, 23, 42);
        Color tarjeta = new Color(51, 37, 61);
        Color dorado = new Color(222, 180, 116);
        Color crema = new Color(248, 226, 197);

        JPanel principal = new JPanel(new BorderLayout(15, 15));
        principal.setBackground(fondo);
        principal.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titulo = new JLabel("JUEGO FINALIZADO", SwingConstants.CENTER);
        titulo.setFont(new Font("Georgia", Font.BOLD, 28));
        titulo.setForeground(dorado);

        JLabel subtitulo = new JLabel("Ganador: " + ganador, SwingConstants.CENTER);
        subtitulo.setFont(new Font("Georgia", Font.BOLD, 22));
        subtitulo.setForeground(crema);

        JPanel encabezado = new JPanel();
        encabezado.setOpaque(false);
        encabezado.setLayout(new BoxLayout(encabezado, BoxLayout.Y_AXIS));
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitulo.setAlignmentX(Component.CENTER_ALIGNMENT);
        encabezado.add(titulo);
        encabezado.add(Box.createVerticalStrut(10));
        encabezado.add(subtitulo);

        JPanel tabla = new JPanel(new GridLayout(resultados.length + 1, 2, 10, 10));
        tabla.setBackground(tarjeta);
        tabla.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(dorado, 2),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel h1 = new JLabel("Jugador", SwingConstants.CENTER);
        h1.setFont(new Font("Georgia", Font.BOLD, 18));
        h1.setForeground(dorado);

        JLabel h2 = new JLabel("Puntos / Saldo", SwingConstants.CENTER);
        h2.setFont(new Font("Georgia", Font.BOLD, 18));
        h2.setForeground(dorado);

        tabla.add(h1);
        tabla.add(h2);

        for (String[] fila : resultados) {
            JLabel nombre = new JLabel(fila[0], SwingConstants.CENTER);
            nombre.setFont(new Font("Arial", Font.PLAIN, 17));
            nombre.setForeground(crema);

            JLabel puntos = new JLabel(fila[1], SwingConstants.CENTER);
            puntos.setFont(new Font("Arial", Font.PLAIN, 17));
            puntos.setForeground(crema);

            tabla.add(nombre);
            tabla.add(puntos);
        }

        JButton boton = new JButton("Aceptar");
        boton.setFont(new Font("Arial", Font.BOLD, 16));
        boton.addActionListener(e -> {
            dispose();
            if (onClose != null) {
                onClose.run();
            }
        });

        JPanel abajo = new JPanel();
        abajo.setOpaque(false);
        abajo.add(boton);

        principal.add(encabezado, BorderLayout.NORTH);
        principal.add(tabla, BorderLayout.CENTER);
        principal.add(abajo, BorderLayout.SOUTH);

        setContentPane(principal);
        setPreferredSize(new Dimension(500, 400));
        pack();
        setLocationRelativeTo(owner);
    }

    public static void mostrar(Component parent, String ganador, String[][] resultados, Runnable onClose) {
        Window owner = parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent);
        PantallaFinal dialog = new PantallaFinal(owner, ganador, resultados, onClose);
        dialog.setVisible(true);
    }
}