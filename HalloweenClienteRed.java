import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Cliente visual de red usando los componentes Halloween originales.
 * El Game local existe UNICAMENTE para dibujar las 24 casillas; nunca procesa
 * tiradas, cambios de turno, compras ni pagos. Servidor.java es la autoridad.
 */
public final class HalloweenClienteRed extends JFrame {
    private static final Color FONDO = new Color(32, 25, 42);
    private static final Color ORO = new Color(222, 180, 116);
    private final String host, nombre;
    private final int puerto;
    private volatile Cliente cliente;
    private final JLabel encabezado = new JLabel("Esperando conexion...", SwingConstants.CENTER);
    private final JLabel estado = new JLabel("", SwingConstants.CENTER);
    private final JButton lanzar = new JButton("Lanzar dados");
    private final JButton comprar = new JButton("Comprar propiedad");
    private final JButton noComprar = new JButton("No comprar");
    private final JButton terminar = new JButton("Terminar turno");
    private final JButton exportar = new JButton("Exportar TXT");
    private final JButton avatar = new JButton("Elegir avatar");
    private final GameLog log = new GameLog();
    private final BoardPanel tablero;
    private final PlayerInfoPanel[] tarjetas = new PlayerInfoPanel[4];
    private final int[] posiciones = {-1, -1, -1, -1};
    private final List<String> transacciones = new ArrayList<>();
    private int miIndice = -1;
    private int turnoActual = -1;
    private boolean pendienteCompra;
    private boolean dadosLanzadosTurno;
    private boolean solicitudDadosPendiente;
    private boolean terminada;
    private boolean exportando;

    public HalloweenClienteRed(String host, int puerto, String nombre) {
        super("Monopoly Halloween - Red | " + nombre);
        this.host = host;
        this.puerto = puerto;
        this.nombre = nombre;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 700));
        setSize(1500, 900);
        setLocationRelativeTo(null);
        getContentPane().setBackground(FONDO);
        setLayout(new BorderLayout(8, 8));

        // Un modelo solo de dibujo, NO es el estado oficial. La UI usa los
        // valores recibidos por ESTADO para mostrar fichas y saldos.
        Game modeloVisual = new Game(4, () -> OptionalLong.empty());
        tablero = new BoardPanel(modeloVisual);
        tablero.setNetworkPositions(posiciones);
        add(tablero, BorderLayout.CENTER);
        add(crearColumna(modeloVisual, true), BorderLayout.WEST);
        add(crearColumna(modeloVisual, false), BorderLayout.EAST);

        JPanel norte = new JPanel(new BorderLayout());
        norte.setOpaque(false);
        encabezado.setFont(new Font("Georgia", Font.BOLD, 28));
        encabezado.setForeground(ORO);
        estado.setForeground(new Color(248, 226, 197));
        norte.add(encabezado, BorderLayout.CENTER);
        norte.add(estado, BorderLayout.SOUTH);
        add(norte, BorderLayout.NORTH);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 7));
        botones.setBackground(FONDO);
        for (JButton b : new JButton[] {lanzar, comprar, noComprar, terminar, avatar, exportar}) {
            b.setBackground(new Color(70, 43, 78));
            b.setForeground(ORO);
            botones.add(b);
        }
        add(botones, BorderLayout.SOUTH);
        lanzar.addActionListener(e -> {
            // La tirada se procesa exclusivamente en el servidor, nunca en este cliente.
            if (cliente == null || solicitudDadosPendiente || dadosLanzadosTurno) return;
            solicitudDadosPendiente = true;
            actualizarBotones();
            enviar(Protocolo.TIRAR_DADOS);
        });
        comprar.addActionListener(e -> enviar(Protocolo.COMPRAR_PROPIEDAD));
        noComprar.addActionListener(e -> enviar(Protocolo.NO_COMPRAR));
        terminar.addActionListener(e -> enviar(Protocolo.TERMINAR_TURNO));
        avatar.addActionListener(e -> elegirAvatar());
        exportar.addActionListener(e -> {
            if (cliente == null || exportando) return;
            exportando = true;
            transacciones.clear();
            cliente.enviar(Protocolo.CONSULTAR_TRANSACCIONES);
        });
        actualizarBotones();
        Thread hilo = new Thread(this::conectar, "Halloween-cliente-TCP");
        hilo.setDaemon(true);
        hilo.start();
    }

    private JPanel crearColumna(Game game, boolean izquierda) {
        JPanel columna = new JPanel(new GridBagLayout());
        columna.setBackground(FONDO);
        columna.setPreferredSize(new Dimension(290, 700));
        int superior = izquierda ? 0 : 1;
        int inferior = izquierda ? 2 : 3;
        tarjetas[superior] = tarjeta(game, superior);
        tarjetas[inferior] = tarjeta(game, inferior);
        colocar(columna, tarjetas[superior], 0, 1);
        colocar(columna, izquierda ? new GameLogPanel(log) : new JailPanel(), 1, 2);
        colocar(columna, tarjetas[inferior], 2, 1);
        return columna;
    }

    private PlayerInfoPanel tarjeta(Game game, int i) {
        PlayerInfoPanel p = new PlayerInfoPanel(game.getPlayer(i), game.getPlayerName(i),
                PlayerColors.forPlayer(i), game.getBank());
        p.setAvatarPlayerIndex(i);
        p.setNetworkState("Jugador " + (i + 1), 1500, true);
        return p;
    }

    private void colocar(JPanel contenedor, java.awt.Component item, int y, double peso) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = y; c.weightx = 1; c.weighty = peso;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(9, 8, 9, 8);
        contenedor.add(item, c);
    }

    private void conectar() {
        try {
            Cliente c = new Cliente(host, puerto,
                    linea -> SwingUtilities.invokeLater(() -> recibir(linea)),
                    e -> SwingUtilities.invokeLater(() -> {
                        estado.setText("Desconectado del servidor");
                        cliente = null;
                        actualizarBotones();
                    }));
            cliente = c;
            c.conectar(nombre);
            SwingUtilities.invokeLater(() -> estado.setText("Servidor " + host + ":" + puerto));
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                estado.setText("Error TCP: " + e.getMessage());
                log.add("No fue posible conectar: " + e.getMessage());
            });
        }
    }

    private void recibir(String linea) {
        String[] p = linea.split("\\|", -1);
        try {
            switch (p[0]) {
                case "BIENVENIDO":
                    miIndice = Integer.parseInt(p[1]);
                    log.add("Conectado como " + p[2]);
                    break;
                case "AVATAR":
                    int jugador = Integer.parseInt(p[1]);
                    int imagen = Integer.parseInt(p[2]);
                    if (jugador >= 0 && jugador < 4) {
                        AvatarSprites.select(jugador, imagen);
                        tarjetas[jugador].repaint();
                        tablero.repaint();
                    }
                    break;
                case "LOBBY":
                    encabezado.setText("SALA DE ESPERA: " + p[1] + "/" + p[2]);
                    break;
                case "INICIO":
                    log.add("Comienza la partida: " + p[1]);
                    break;
                case "TURNO":
                    turnoActual = Integer.parseInt(p[1]);
                    pendienteCompra = false;
                    dadosLanzadosTurno = false;
                    solicitudDadosPendiente = false;
                    encabezado.setText("Turno " + p[3] + ": " + p[2]);
                    log.add("Turno de " + p[2] + ". Usa Lanzar dados o el boton fisico de la Pico.");
                    break;
                case "ESTADO":
                    actualizarEstado(p);
                    break;
                case "DADOS":
                    dadosLanzadosTurno = true;
                    solicitudDadosPendiente = false;
                    log.add("Dados: " + p[2] + " + " + p[3] + " / " + p[6]);
                    break;
                case "PUEDE_COMPRAR":
                    pendienteCompra = true;
                    log.add("Puedes comprar " + p[1] + " por " + p[2]);
                    break;
                case "COMPRA":
                    pendienteCompra = false;
                    log.add(p.length > 2 ? p[2] : linea);
                    break;
                case "TRANSACCIONES":
                    if (exportando) transacciones.clear();
                    break;
                case "TX":
                    if (exportando && p.length > 2)
                        transacciones.add("Transaccion #: " + p[1] + "\n" + p[2].replace(" ; ", "\n"));
                    break;
                case "FIN_TRANSACCIONES":
                    if (exportando) { exportando = false; guardarTXT(); }
                    break;
                case "ERROR":
                    solicitudDadosPendiente = false;
                    log.add(Cliente.formatear(linea));
                    break;
                case "FIN":
                    terminada = true;
                    encabezado.setText("Partida terminada");
                    log.add(Cliente.formatear(linea));
                    break;
                default:
                    String mensaje = Cliente.formatear(linea);
                    if (!mensaje.isEmpty()) log.add(mensaje);
            }
        } catch (RuntimeException error) {
            log.add("No se interpreto mensaje TCP: " + linea);
        }
        actualizarBotones();
    }

    private void actualizarEstado(String[] p) {
        if (p.length < 3) return;
        turnoActual = Integer.parseInt(p[1]);
        for (int i = 0; i < posiciones.length; i++) posiciones[i] = -1;
        for (String item : p[2].split(";")) {
            String[] v = item.split(",", -1);
            if (v.length < 5) continue;
            int i = Integer.parseInt(v[0]);
            if (i < 0 || i >= 4) continue;
            int saldo = Integer.parseInt(v[2]);
            posiciones[i] = Integer.parseInt(v[3]);
            tarjetas[i].setNetworkState(v[1], saldo, Boolean.parseBoolean(v[4]));
        }
        tablero.setNetworkPositions(posiciones);
    }

    private void actualizarBotones() {
        boolean turnoPropio = cliente != null && miIndice >= 0 && miIndice == turnoActual && !terminada;
        lanzar.setEnabled(turnoPropio && !dadosLanzadosTurno && !solicitudDadosPendiente && !pendienteCompra);
        comprar.setEnabled(turnoPropio && pendienteCompra);
        noComprar.setEnabled(turnoPropio && pendienteCompra);
        terminar.setEnabled(turnoPropio);
        avatar.setEnabled(cliente != null && miIndice >= 0 && !terminada);
        exportar.setEnabled(cliente != null);
    }

    private void elegirAvatar() {
        if (cliente == null || miIndice < 0) return;
        int elegido = HalloweenDialogs.avatar(this, miIndice);
        if (elegido >= 0) enviar(Protocolo.ELEGIR_AVATAR + "|" + elegido);
    }

    private void enviar(String cmd) { if (cliente != null) cliente.enviar(cmd); }

    private void guardarTXT() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Exportar transacciones del servidor");
        selector.setSelectedFile(new File("transacciones_monopoly_red.txt"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File archivo = selector.getSelectedFile();
        if (!archivo.getName().toLowerCase().endsWith(".txt"))
            archivo = new File(archivo.getParentFile(), archivo.getName() + ".txt");
        if (archivo.exists() && JOptionPane.showConfirmDialog(this,
                "¿Sobrescribir el archivo existente?", "Confirmacion", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return;
        StringBuilder texto = new StringBuilder("MONOPOLY - TRANSACCIONES DEL SERVIDOR\n");
        texto.append("Total: ").append(transacciones.size()).append("\n\n");
        if (transacciones.isEmpty()) texto.append("Sin transacciones economicas.\n");
        for (String t : transacciones) texto.append("-------------------------\n").append(t).append("\n\n");
        try {
            Files.writeString(archivo.toPath(), texto.toString(), StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Archivo TXT guardado: " + archivo);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error al exportar TXT: " + e.getMessage());
        }
    }

    @Override public void dispose() {
        if (cliente != null) cliente.close();
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String host = args.length > 0 ? args[0] :
                    JOptionPane.showInputDialog(null, "IP de la PC organizadora", "127.0.0.1");
            if (host == null || host.isBlank()) return;
            int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 6000;
            String nombre = args.length > 2 ? args[2] :
                    JOptionPane.showInputDialog(null, "Nombre del jugador", "Jugador");
            if (nombre == null || nombre.isBlank()) return;
            new HalloweenClienteRed(host.trim(), puerto, nombre.trim()).setVisible(true);
        });
    }
}
