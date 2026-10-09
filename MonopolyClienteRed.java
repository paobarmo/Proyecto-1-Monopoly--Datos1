import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** Cliente gráfico de RED: muestra únicamente lo que notifica el Servidor oficial. */
public class MonopolyClienteRed extends JFrame {
    private final String host;
    private final int puerto;
    private final String nombre;
    private Cliente cliente;
    private final JLabel conexion = new JLabel("Conectando...", SwingConstants.CENTER);
    private final JLabel turno = new JLabel("Esperando jugadores...", SwingConstants.CENTER);
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[] {"Jugador", "Nombre", "Saldo", "Casilla", "Activo"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JLabel[] casillas = new JLabel[24];
    private final int[] posiciones = {-1, -1, -1, -1};
    private final String[] nombres = {"", "", "", ""};
    private final JTextArea eventos = new JTextArea();
    private final JButton comprar = new JButton("Comprar propiedad");
    private final JButton noComprar = new JButton("No comprar");
    private final JButton terminar = new JButton("Terminar turno");
    private final JButton exportar = new JButton("Exportar transacciones TXT");
    private final List<String> datosExportados = new ArrayList<>();
    private boolean esperandoExportacion;
    private int miIndice = -1;

    public MonopolyClienteRed(String host, int puerto, String nombre) {
        super("Monopoly distribuido | " + nombre);
        this.host = host;
        this.puerto = puerto;
        this.nombre = nombre;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(920, 650));
        setSize(1220, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        JPanel encabezado = new JPanel(new GridLayout(2, 1));
        turno.setFont(new Font("SansSerif", Font.BOLD, 24));
        encabezado.add(turno);
        encabezado.add(conexion);
        add(encabezado, BorderLayout.NORTH);

        JPanel tablero = new JPanel(new GridLayout(4, 6, 5, 5));
        tablero.setBorder(BorderFactory.createTitledBorder("Tablero (posiciones oficiales del servidor)"));
        for (int i = 0; i < 24; i++) {
            JLabel c = new JLabel("", SwingConstants.CENTER);
            c.setOpaque(true);
            c.setBackground(new Color(239, 236, 229));
            c.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            casillas[i] = c;
            tablero.add(c);
        }
        refrescarTablero();
        add(tablero, BorderLayout.CENTER);

        JPanel derecha = new JPanel(new BorderLayout(3, 3));
        derecha.setPreferredSize(new Dimension(360, 100));
        derecha.add(new JScrollPane(new JTable(modelo)), BorderLayout.NORTH);
        eventos.setEditable(false);
        eventos.setLineWrap(true);
        eventos.setWrapStyleWord(true);
        derecha.add(new JScrollPane(eventos), BorderLayout.CENTER);
        add(derecha, BorderLayout.EAST);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel dados = new JLabel("Dados: usa el boton fisico de la Pico W");
        botones.add(dados);
        botones.add(comprar);
        botones.add(noComprar);
        botones.add(terminar);
        botones.add(exportar);
        add(botones, BorderLayout.SOUTH);
        comprar.addActionListener(e -> enviar(Protocolo.COMPRAR_PROPIEDAD));
        noComprar.addActionListener(e -> enviar(Protocolo.NO_COMPRAR));
        terminar.addActionListener(e -> enviar(Protocolo.TERMINAR_TURNO));
        exportar.addActionListener(e -> {
            if (cliente == null) return;
            esperandoExportacion = true;
            datosExportados.clear();
            cliente.enviar(Protocolo.CONSULTAR_TRANSACCIONES);
        });
        habilitarBotones(false);
        // Nunca bloquear el hilo Swing mientras espera una conexión TCP.
        Thread conexionHilo = new Thread(this::conectar, "cliente-red-conectar");
        conexionHilo.setDaemon(true);
        conexionHilo.start();
    }

    private void conectar() {
        try {
            Cliente c = new Cliente(host, puerto,
                    linea -> SwingUtilities.invokeLater(() -> recibir(linea)),
                    err -> SwingUtilities.invokeLater(() -> {
                        conexion.setText("Desconectado del servidor");
                        habilitarBotones(false);
                    }));
            cliente = c;
            c.conectar(nombre);
            SwingUtilities.invokeLater(() -> conexion.setText(
                    "Servidor: " + host + ":" + puerto + " | " + nombre));
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                conexion.setText("No se pudo conectar: " + e.getMessage());
                registro("ERROR de conexion: " + e.getMessage());
            });
        }
    }

    private void recibir(String linea) {
        String[] p = linea.split("\\|", -1);
        try {
            switch (p[0]) {
                case "BIENVENIDO":
                    miIndice = Integer.parseInt(p[1]);
                    registro("Conectado como " + p[2] + " (jugador " + (miIndice + 1) + ")");
                    break;
                case "LOBBY":
                    turno.setText("Esperando jugadores: " + p[1] + "/" + p[2]);
                    break;
                case "INICIO":
                    registro("Partida iniciada: " + p[1]);
                    break;
                case "TURNO":
                    turno.setText("Turno " + p[3] + ": " + p[2]);
                    break;
                case "ESTADO":
                    actualizarEstado(p);
                    break;
                case "TRANSACCIONES":
                    if (esperandoExportacion) datosExportados.clear();
                    break;
                case "TX":
                    if (esperandoExportacion && p.length >= 3) datosExportados.add(
                            "Transaccion #: " + p[1] + "\n" + p[2].replace(" ; ", "\n"));
                    break;
                case "FIN_TRANSACCIONES":
                    if (esperandoExportacion) {
                        esperandoExportacion = false;
                        guardarTXT();
                    }
                    break;
                case "PUEDE_COMPRAR":
                    registro("Puedes comprar " + p[1] + " por " + p[2]);
                    break;
                default:
                    String texto;
                    try { texto = Cliente.formatear(linea); }
                    catch (RuntimeException ex) { texto = linea; }
                    if (!texto.isEmpty()) registro(texto);
            }
        } catch (RuntimeException ex) {
            registro("Mensaje recibido: " + linea);
        }
    }

    private void actualizarEstado(String[] p) {
        if (p.length < 3) return;
        int activo = Integer.parseInt(p[1]);
        modelo.setRowCount(0);
        for (int i = 0; i < posiciones.length; i++) { posiciones[i] = -1; nombres[i] = ""; }
        for (String entrada : p[2].split(";")) {
            String[] d = entrada.split(",", -1);
            if (d.length < 5) continue;
            int idx = Integer.parseInt(d[0]);
            if (idx < 0 || idx >= posiciones.length) continue;
            nombres[idx] = d[1];
            posiciones[idx] = Integer.parseInt(d[3]);
            modelo.addRow(new Object[] {idx + 1, d[1], d[2], d[3], d[4].equals("true") ? "Si" : "No"});
        }
        refrescarTablero();
        habilitarBotones(miIndice == activo);
    }

    private void refrescarTablero() {
        for (int i = 0; i < casillas.length; i++) {
            StringBuilder etiqueta = new StringBuilder("<html><center>Casilla " + i);
            for (int j = 0; j < posiciones.length; j++) {
                if (posiciones[j] == i) etiqueta.append("<br><b>J").append(j + 1).append("</b>");
            }
            etiqueta.append("</center></html>");
            casillas[i].setText(etiqueta.toString());
        }
    }

    private void habilitarBotones(boolean propioTurno) {
        comprar.setEnabled(propioTurno);
        noComprar.setEnabled(propioTurno);
        terminar.setEnabled(propioTurno);
        exportar.setEnabled(cliente != null);
    }

    private void enviar(String comando) { if (cliente != null) cliente.enviar(comando); }
    private void registro(String texto) {
        eventos.append(texto + "\n");
        eventos.setCaretPosition(eventos.getDocument().getLength());
    }

    private void guardarTXT() {
        JFileChooser elegir = new JFileChooser();
        elegir.setDialogTitle("Guardar transacciones economicas del SERVIDOR");
        elegir.setSelectedFile(new File("transacciones_monopoly_red.txt"));
        if (elegir.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File archivo = elegir.getSelectedFile();
        if (!archivo.getName().toLowerCase().endsWith(".txt"))
            archivo = new File(archivo.getParentFile(), archivo.getName() + ".txt");
        if (archivo.exists() && JOptionPane.showConfirmDialog(this,
                "El archivo ya existe. ¿Sobrescribir?", "Confirmar",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        StringBuilder texto = new StringBuilder("MONOPOLY - TRANSACCIONES DE LA PARTIDA EN RED\n");
        texto.append("Total: ").append(datosExportados.size()).append("\n\n");
        if (datosExportados.isEmpty()) texto.append("Sin transacciones economicas.\n");
        else for (String registro : datosExportados)
            texto.append("------------------------------------\n").append(registro).append("\n\n");
        try {
            Files.writeString(archivo.toPath(), texto.toString(), StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Archivo guardado: " + archivo);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + e.getMessage());
        }
    }

    @Override public void dispose() {
        if (cliente != null) cliente.close();
        super.dispose();
    }

    /** Segunda PC: ejecutar y escribir IP del organizador y nombre del jugador. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String ip = args.length > 0 ? args[0]
                    : JOptionPane.showInputDialog(null, "IP del organizador:", "192.168.1.10");
            if (ip == null || ip.isBlank()) return;
            int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 6000;
            String nombre = args.length > 2 ? args[2]
                    : JOptionPane.showInputDialog(null, "Tu nombre en la partida:", "Jugador");
            if (nombre == null || nombre.isBlank()) return;
            new MonopolyClienteRed(ip.trim(), puerto, nombre.trim()).setVisible(true);
        });
    }
}
