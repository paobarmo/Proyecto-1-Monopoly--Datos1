import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Ventanas estéticas de selección. No modifica las acciones del Monopoly. */
public final class HalloweenDialogs {
    private static final Color FONDO = new Color(32, 25, 42);
    private static final Color PANEL = new Color(53, 38, 62);
    private static final Color ORO = new Color(226, 186, 124);
    private static final Color TEXTO = new Color(250, 230, 201);

    private HalloweenDialogs() {}

    /** Devuelve los mismos índices que JOptionPane: 0..3, o -1 si se cierra. */
    public static int modo(Component parent) {
        return mostrar(parent, "Elige cómo jugar", "¿CÓMO QUIERES JUGAR?",
                "Tu aventura de Monopoly Halloween comienza aquí",
                new String[]{"Crear partida", "Unirse a partida", "Jugar local", "Cancelar"}, false);
    }

    /** Devuelve el índice del avatar seleccionado, o -1 si se cancela. */
    public static int avatar(Component parent, int jugador) {
        String[] opciones = new String[AvatarSprites.count()];
        for (int i = 0; i < opciones.length; i++) opciones[i] = AvatarSprites.name(i);
        return mostrar(parent, "Elegir avatar", "ELIGE TU PERSONAJE",
                "Jugador " + (jugador + 1) + " · Escoge tu compañero de aventura", opciones, true);
    }

    private static int mostrar(Component parent, String ventana, String titulo,
                               String descripcion, String[] opciones, boolean conAvatares) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, ventana, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        final int[] resultado = {-1};

        JPanel body = new JPanel(new BorderLayout(0, 19));
        body.setBackground(FONDO);
        body.setBorder(new EmptyBorder(27, 28, 25, 28));

        JPanel top = new JPanel(new GridLayout(2, 1, 0, 5));
        top.setOpaque(false);
        JLabel heading = new JLabel(titulo, SwingConstants.CENTER);
        heading.setFont(new Font("Georgia", Font.BOLD, 25));
        heading.setForeground(ORO);
        JLabel subtitle = new JLabel(descripcion, SwingConstants.CENTER);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(TEXTO);
        top.add(heading);
        top.add(subtitle);
        body.add(top, BorderLayout.NORTH);

        JPanel choices = new JPanel(new GridLayout(1, opciones.length, 12, 0));
        choices.setOpaque(false);
        for (int i = 0; i < opciones.length; i++) {
            final int index = i;
            JButton button = new JButton(opciones[i]);
            button.setBackground(PANEL);
            button.setForeground(TEXTO);
            button.setFont(new Font("Georgia", Font.BOLD, conAvatares ? 13 : 15));
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ORO, 2, true), new EmptyBorder(12, 8, 12, 8)));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (conAvatares) {
                Image image = AvatarSprites.forAvatar(i);
                if (image != null) {
                    Image scaled = image.getScaledInstance(86, 86, Image.SCALE_FAST);
                    button.setIcon(new ImageIcon(scaled));
                }
                button.setHorizontalTextPosition(SwingConstants.CENTER);
                button.setVerticalTextPosition(SwingConstants.BOTTOM);
                button.setIconTextGap(12);
                button.setPreferredSize(new Dimension(150, 145));
            } else {
                button.setPreferredSize(new Dimension(155, 63));
            }
            button.addActionListener(e -> {
                resultado[0] = index;
                dialog.dispose();
            });
            choices.add(button);
        }
        body.add(choices, BorderLayout.CENTER);
        dialog.setContentPane(body);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return resultado[0];
    }

    private static JDialog dialogo(Component parent, String titulo) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Monopoly Halloween", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        return dialog;
    }

    private static JPanel base(String titulo, String detalle) {
        JPanel body = new JPanel(new BorderLayout(0, 22));
        body.setBackground(FONDO);
        body.setBorder(new EmptyBorder(28, 35, 28, 35));
        JPanel encabezado = new JPanel(new GridLayout(2, 1, 0, 8));
        encabezado.setOpaque(false);
        JLabel title = new JLabel(titulo, SwingConstants.CENTER);
        title.setFont(new Font("Georgia", Font.BOLD, 25));
        title.setForeground(ORO);
        JLabel description = new JLabel(detalle, SwingConstants.CENTER);
        description.setFont(new Font("SansSerif", Font.PLAIN, 14));
        description.setForeground(TEXTO);
        encabezado.add(title);
        encabezado.add(description);
        body.add(encabezado, BorderLayout.NORTH);
        return body;
    }

    private static JButton boton(String texto) {
        JButton button = new JButton(texto);
        button.setFont(new Font("Georgia", Font.BOLD, 16));
        button.setForeground(TEXTO);
        button.setBackground(PANEL);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ORO, 2, true), new EmptyBorder(11, 22, 11, 22)));
        return button;
    }

    private static void preparar(JDialog dialog, JPanel body, Component parent) {
        dialog.setContentPane(body);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
    }

    /** Igual que showInputDialog: devuelve null al cancelar o cerrar. */
    public static String entrada(Component parent, String titulo, String valorInicial) {
        JDialog dialog = dialogo(parent, titulo);
        final String[] resultado = {null};
        JPanel body = base(titulo, "Monopoly Halloween");
        JTextField campo = new JTextField(valorInicial, 25);
        campo.setFont(new Font("Georgia", Font.PLAIN, 18));
        campo.setForeground(TEXTO);
        campo.setBackground(PANEL);
        campo.setCaretColor(ORO);
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ORO, 1), new EmptyBorder(10, 12, 10, 12)));
        body.add(campo, BorderLayout.CENTER);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        acciones.setOpaque(false);
        JButton ok = boton("Confirmar");
        JButton cancelar = boton("Cancelar");
        ok.addActionListener(e -> { resultado[0] = campo.getText(); dialog.dispose(); });
        cancelar.addActionListener(e -> dialog.dispose());
        acciones.add(ok);
        acciones.add(cancelar);
        body.add(acciones, BorderLayout.SOUTH);
        preparar(dialog, body, parent);
        dialog.getRootPane().setDefaultButton(ok);
        SwingUtilities.invokeLater(() -> { campo.requestFocusInWindow(); campo.selectAll(); });
        dialog.setVisible(true);
        return resultado[0];
    }

    /** Devuelve true solo al confirmar. */
    public static boolean confirmar(Component parent, String titulo, String mensaje) {
        JDialog dialog = dialogo(parent, titulo);
        final boolean[] resultado = {false};
        JPanel body = base(titulo, "Confirma para continuar");
        JLabel texto = new JLabel("<html><div style='text-align:center;'>" +
                escaparHtml(mensaje).replace("\n", "<br>") + "</div></html>", SwingConstants.CENTER);
        texto.setForeground(TEXTO);
        texto.setFont(new Font("SansSerif", Font.PLAIN, 16));
        body.add(texto, BorderLayout.CENTER);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        acciones.setOpaque(false);
        JButton ok = boton("Crear partida");
        JButton cancelar = boton("Cancelar");
        ok.addActionListener(e -> { resultado[0] = true; dialog.dispose(); });
        cancelar.addActionListener(e -> dialog.dispose());
        acciones.add(ok); acciones.add(cancelar);
        body.add(acciones, BorderLayout.SOUTH);
        preparar(dialog, body, parent);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.setVisible(true);
        return resultado[0];
    }

    /** Mensajes informativos y errores con el mismo estilo. */
    public static void mensaje(Component parent, String titulo, String mensaje) {
        JDialog dialog = dialogo(parent, titulo);
        JPanel body = base(titulo, "Monopoly Halloween");
        JLabel texto = new JLabel("<html><div style='text-align:center;'>" +
                escaparHtml(mensaje).replace("\n", "<br>") + "</div></html>", SwingConstants.CENTER);
        texto.setForeground(TEXTO);
        texto.setFont(new Font("SansSerif", Font.PLAIN, 16));
        body.add(texto, BorderLayout.CENTER);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.CENTER));
        acciones.setOpaque(false);
        JButton ok = boton("Aceptar");
        ok.addActionListener(e -> dialog.dispose());
        acciones.add(ok);
        body.add(acciones, BorderLayout.SOUTH);
        preparar(dialog, body, parent);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.setVisible(true);
    }

    private static String escaparHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Mantiene el mismo JDialog no modal de espera, solo cambia su apariencia. */
    public static void estilizarEspera(JDialog dialog, JLabel progress) {
        JPanel body = base("SALA DE ESPERA", "Conexión multijugador");
        progress.setForeground(TEXTO);
        progress.setFont(new Font("SansSerif", Font.PLAIN, 16));
        body.add(progress, BorderLayout.CENTER);
        dialog.setContentPane(body);
        dialog.setMinimumSize(new Dimension(510, 175));
        dialog.pack();
        dialog.setLocationRelativeTo(dialog.getOwner());
    }

}
