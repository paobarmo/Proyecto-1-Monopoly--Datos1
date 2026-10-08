import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.net.ServerSocket;
import java.net.Socket;

import java.util.Random;


public class ServidorHardware {


    // Puerto donde Java va a escuchar a la Pico.
    //
    // Debe ser el MISMO que aparece en main.py.

    private static final int PUERTO = 5000;


    // Esto genera numeros aleatorios
    // para los dados.

    private static final Random random =
            new Random();



    public static void main(String[] args) {


        try {


            // Esto abre el puerto 5000
            // y convierte a Java en servidor.

            ServerSocket servidor =
                    new ServerSocket(PUERTO);



            System.out.println(
                    "=============================="
            );

            System.out.println(
                    "SERVIDOR HARDWARE INICIADO"
            );

            System.out.println(
                    "Puerto: " + PUERTO
            );

            System.out.println(
                    "Esperando Raspberry Pi Pico W..."
            );

            System.out.println(
                    "=============================="
            );



            // El servidor se queda encendido
            // esperando eventos del hardware.

            while (true) {


                // Java espera aqui hasta que
                // la Pico se conecte.

                Socket pico =
                        servidor.accept();



                // Esto sirve para LEER
                // lo que mande la Pico.

                BufferedReader entrada =
                        new BufferedReader(

                                new InputStreamReader(

                                        pico.getInputStream()
                                )
                        );



                // Esto sirve para RESPONDER
                // a la Pico.

                PrintWriter salida =
                        new PrintWriter(

                                pico.getOutputStream(),

                                true
                        );



                // Leemos el mensaje enviado
                // por la Raspberry.

                String mensaje =
                        entrada.readLine();



                System.out.println();

                System.out.println(
                        "Pico envio:"
                );

                System.out.println(
                        mensaje
                );



                // =================================================
                // BOTON DE DADOS
                // =================================================

                if (
                        mensaje.equals(
                                "TIRAR_DADOS"
                        )
                ) {


                    // Aqui se generan los dados OFICIALES.
                    //
                    // random.nextInt(6)
                    // genera:
                    //
                    // 0 1 2 3 4 5
                    //
                    // +1 lo convierte en:
                    //
                    // 1 2 3 4 5 6

                    int dado1 =
                            random.nextInt(6) + 1;


                    int dado2 =
                            random.nextInt(6) + 1;



                    int total =
                            dado1 + dado2;



                    // Creamos algo como:
                    //
                    // DADOS|4|6

                    String respuesta =
                            "DADOS"
                            + "|"
                            + dado1
                            + "|"
                            + dado2;



                    // Se lo mandamos a la Pico.

                    salida.println(
                            respuesta
                    );



                    System.out.println(
                            "RESULTADO DE DADOS"
                    );

                    System.out.println(
                            "Dado 1: " + dado1
                    );

                    System.out.println(
                            "Dado 2: " + dado2
                    );

                    System.out.println(
                            "Total: " + total
                    );



                    // =================================================
                    // AQUI YA TIENES LOS RETURNS / VARIABLES
                    // PARA USAR EN TU JUEGO
                    // =================================================

                    procesarDados(
                            dado1,
                            dado2
                    );
                }



                // =================================================
                // RFID
                // =================================================

                else if (
                        mensaje.startsWith(
                                "RFID|"
                        )
                ) {


                    // La Pico manda algo como:
                    //
                    // RFID|123456789
                    //
                    // split separa:
                    //
                    // RFID
                    // 123456789

                    String[] partes =
                            mensaje.split(
                                    "\\|"
                            );



                    String uid =
                            partes[1];



                    System.out.println(
                            "TARJETA RFID"
                    );

                    System.out.println(
                            "UID: " + uid
                    );



                    // Le confirmamos a la Pico
                    // que recibimos la tarjeta.

                    salida.println(
                            "RFID_OK|" + uid
                    );



                    // Aqui ya tienes el UID
                    // como String normal de Java.

                    procesarRFID(
                            uid
                    );
                }



                // =================================================
                // MENSAJE RARO
                // =================================================

                else {


                    salida.println(
                            "ERROR|COMANDO_DESCONOCIDO"
                    );


                    System.out.println(
                            "Comando desconocido"
                    );
                }



                // Cerramos ESTA conexion.
                //
                // El servidor sigue encendido
                // esperando la siguiente.

                pico.close();
            }


        }

        catch (IOException e) {


            System.out.println(
                    "ERROR EN EL SERVIDOR"
            );


            e.printStackTrace();
        }
    }



    // =========================================================
    // RESULTADO DE LOS DADOS
    // =========================================================

    public static ResultadoDados procesarDados(
            int dado1,
            int dado2
    ) {


        // Creamos un objeto donde guardamos
        // los dos resultados.

        ResultadoDados resultado =
                new ResultadoDados(
                        dado1,
                        dado2
                );



        System.out.println();

        System.out.println(
                "Resultado disponible para el juego:"
        );

        System.out.println(
                "Dado 1 = "
                + resultado.getDado1()
        );

        System.out.println(
                "Dado 2 = "
                + resultado.getDado2()
        );

        System.out.println(
                "Movimiento = "
                + resultado.getTotal()
        );



        // ESTE ES EL RETURN QUE PUEDES USAR
        // DESPUES EN TU JUEGO.

        return resultado;
    }



    // =========================================================
    // RESULTADO DEL RFID
    // =========================================================

    public static String procesarRFID(
            String uid
    ) {


        System.out.println();

        System.out.println(
                "UID disponible para el juego:"
        );

        System.out.println(
                uid
        );



        // ESTE ES EL RETURN DEL RFID.

        return uid;
    }



    // =========================================================
    // CLASE PARA GUARDAR LOS DOS DADOS
    // =========================================================

    public static class ResultadoDados {


        private int dado1;

        private int dado2;



        public ResultadoDados(
                int dado1,
                int dado2
        ) {


            this.dado1 =
                    dado1;


            this.dado2 =
                    dado2;
        }



        // Devuelve el primer dado.

        public int getDado1() {

            return dado1;
        }



        // Devuelve el segundo dado.

        public int getDado2() {

            return dado2;
        }



        // Devuelve la suma de ambos.

        public int getTotal() {

            return dado1 + dado2;
        }
    }
}