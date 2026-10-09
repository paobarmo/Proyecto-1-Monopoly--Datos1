import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Ventana modal que muestra una carta de Fortuna hasta que se pulsa Aceptar. */
public final class CartaEventoDialog {

    private CartaEventoDialog() {
    }

    /** Debe llamarse desde el hilo de Swing. Bloquea hasta que se cierre. */
    public static void mostrar(Component parent, CartaEvento carta) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Fortuna", JDialog.ModalityType.APPLICATION_MODAL);

        JLabel titulo = new JLabel("FORTUNA", SwingConstants.CENTER);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 28));

        JLabel texto = new JLabel("<html><div style='text-align:center;width:300px'>"
                + carta.getTexto() + "</div></html>", SwingConstants.CENTER);
        texto.setFont(new Font("SansSerif", Font.PLAIN, 20));

        JButton aceptar = new JButton("Aceptar");
        aceptar.setFont(new Font("SansSerif", Font.BOLD, 20));
        aceptar.setFocusPainted(false);
        aceptar.addActionListener(e -> dialog.dispose());

        JPanel contenido = new JPanel(new BorderLayout(0, 20));
        contenido.setBackground(new Color(255, 245, 200));
        contenido.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 3),
                BorderFactory.createEmptyBorder(25, 30, 25, 30)));
        contenido.add(titulo, BorderLayout.NORTH);
        contenido.add(texto, BorderLayout.CENTER);
        contenido.add(aceptar, BorderLayout.SOUTH);

        dialog.setContentPane(contenido);
        dialog.setMinimumSize(new Dimension(400, 250));
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.getRootPane().setDefaultButton(aceptar);
        dialog.setVisible(true);
    }
}