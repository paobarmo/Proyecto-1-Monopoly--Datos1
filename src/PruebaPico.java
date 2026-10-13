import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Prueba el puerto de la Pico SIN hardware fisico. */
public class PruebaPico {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Uso: java PruebaPico TIRAR_DADOS");
            System.out.println("     java PruebaPico \"RFID|123456789\"");
            return;
        }
        try (Socket conexion = new Socket("127.0.0.1", 5001)) {
            conexion.setSoTimeout(5000);
            PrintWriter salida = new PrintWriter(conexion.getOutputStream(), true,
                    StandardCharsets.UTF_8);
            BufferedReader entrada = new BufferedReader(new InputStreamReader(
                    conexion.getInputStream(), StandardCharsets.UTF_8));
            salida.println(args[0]);
            System.out.println("Pico simulada envio: " + args[0]);
            System.out.println("Servidor respondio: " + entrada.readLine());
        }
    }
}
