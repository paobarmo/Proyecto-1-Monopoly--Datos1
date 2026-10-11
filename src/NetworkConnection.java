import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** First networking step: TCP handshake. This does NOT synchronize Game yet. */
public final class NetworkConnection {
    public static final int PORT = 5050;
    private NetworkConnection() {}

    public static String hostOnce() throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            server.setSoTimeout(60000);
            try (Socket peer = server.accept()) {
                peer.setSoTimeout(10000);
                BufferedReader in = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(peer.getOutputStream(), true);
                if (!"MONOPOLY_HELLO".equals(in.readLine())) {
                    throw new IOException("El cliente envió un mensaje desconocido.");
                }
                out.println("MONOPOLY_OK");
                return "Conexión recibida desde " + peer.getInetAddress().getHostAddress();
            }
        }
    }

    public static String joinOnce(String address) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(address, PORT), 10000);
            socket.setSoTimeout(10000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println("MONOPOLY_HELLO");
            if (!"MONOPOLY_OK".equals(in.readLine())) {
                throw new IOException("El servidor no confirmó la conexión.");
            }
            return "Conexión correcta con " + address;
        }
    }
}
