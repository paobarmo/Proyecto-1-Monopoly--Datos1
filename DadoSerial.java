import com.fazecast.jSerialComm.SerialPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.function.BiConsumer;

/**
 * Lee las tiradas de dados que envía la Raspberry Pi Pico por USB serial.
 * Espera líneas con el formato "DADOS,<dado1>,<dado2>".
 */
public class DadoSerial implements AutoCloseable {

    private static final String PREFIX = "DADOS,";

    private final SerialPort port;
    private final Thread readerThread;
    private volatile boolean running = true;

    /**
     * @param onRoll se llama cada vez que llega una tirada (dado1, dado2).
     *               OJO: se ejecuta en un hilo aparte, no en el de Swing.
     */
    public DadoSerial(BiConsumer<Integer, Integer> onRoll) {
        this.port = findPico();
        port.setBaudRate(115200);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);

        if (!port.openPort()) {
            throw new IllegalStateException(
                    "No se pudo abrir " + port.getSystemPortName()
                            + ". ¿Está abierto Thonny u otro programa?");
        }

        readerThread = new Thread(() -> readLoop(onRoll), "dado-serial-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop(BiConsumer<Integer, Integer> onRoll) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(port.getInputStream()))) {

            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith(PREFIX)) {
                    continue; // otros prints de la Pico: se ignoran
                }
                String[] parts = line.substring(PREFIX.length()).split(",");
                try {
                    int d1 = Integer.parseInt(parts[0].trim());
                    int d2 = Integer.parseInt(parts[1].trim());
                    onRoll.accept(d1, d2);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    System.err.println("Línea de dados inválida: " + line);
                }
            }
        } catch (IOException e) {
            if (running) e.printStackTrace();
        }
    }

    /** Busca el puerto de la Pico (MicroPython aparece como "USB Serial"/"Board CDC"). */
    private static SerialPort findPico() {
        for (SerialPort p : SerialPort.getCommPorts()) {
            String desc = p.getDescriptivePortName().toLowerCase();
            if (desc.contains("usb serial") || desc.contains("board cdc")
                    || desc.contains("pico") || desc.contains("micropython")) {
                return p;
            }
        }
        // Si no lo reconoce, lista lo que hay para que elijas a mano.
        for (SerialPort p : SerialPort.getCommPorts()) {
            System.out.println("Puerto disponible: " + p.getSystemPortName()
                    + " - " + p.getDescriptivePortName());
        }
        throw new IllegalStateException("No se encontró la Pico. Revisa el cable USB.");
    }

    @Override
    public void close() {
        running = false;
        port.closePort();
    }
}