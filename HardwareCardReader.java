import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Adaptador RFID para Bank y Game: implementa el CardReader que ya usan.
 * La tarjeta se consume SOLO cuando el banco/juego esta esperando un escaneo.
 * De esa manera no se reutiliza accidentalmente una tarjeta pasada.
 */
public final class HardwareCardReader implements CardReader {
    private final ArrayBlockingQueue<Long> tarjetas = new ArrayBlockingQueue<>(1);
    private volatile boolean esperandoTarjeta;

    // Llamado por el puente cuando ServidorHardware confirma RFID_OK|uid.
    public void recibirUID(String uid) {
        try {
            long numero = convertirUID(uid);
            if (esperandoTarjeta) {
                tarjetas.offer(numero);
                System.out.println("RFID recibido para una verificacion: " + uid);
            } else {
                System.out.println("RFID ignorado: ninguna operacion esta pidiendo tarjeta");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("UID invalido: " + uid + "; " + e.getMessage());
        }
    }

    /**
     * Bank.verify() ya llama a este metodo.
     * Espera hasta 45 segundos para que acerquen una tarjeta; si no llega,
     * la operacion falla sin modificar el saldo.
     */
    @Override
    public synchronized OptionalLong readCardId() {
        tarjetas.clear();
        esperandoTarjeta = true;
        System.out.println("ACERCA TU TARJETA RFID (45 segundos)...");
        try {
            Long uid = tarjetas.poll(45, TimeUnit.SECONDS);
            return uid == null ? OptionalLong.empty() : OptionalLong.of(uid);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OptionalLong.empty();
        } finally {
            esperandoTarjeta = false;
            tarjetas.clear();
        }
    }

    /**
     * UID decimal: 12345678.
     * UID hexadecimal: 0xA1B2 / A1:B2:03:04 / A1 B2 03 04.
     * Bank compara IDs como long, por eso se convierte el texto a long.
     */
    public static long convertirUID(String texto) {
        if (texto == null || texto.trim().isEmpty())
            throw new IllegalArgumentException("UID vacio");
        String uid = texto.trim().toUpperCase(Locale.ROOT);
        if (uid.matches("[0-9]+")) {
            try { return Long.parseLong(uid); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("UID decimal fuera del rango de long", e);
            }
        }
        uid = uid.replace("0X", "").replace(":", "").replace("-", "")
                .replace(" ", "");
        if (!uid.matches("[0-9A-F]+"))
            throw new IllegalArgumentException("Solo se aceptan UID decimales o hexadecimales");
        try { return Long.parseUnsignedLong(uid, 16); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("UID hexadecimal demasiado largo", e);
        }
    }
}
