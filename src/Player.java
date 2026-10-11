/**
 * TEMPORARY TEST
 * DELETE THIS
 * Do not merge, delete, this is just for testing not the real one.
 * Replace this file entirely once the real Player class is provided.
 */

public class Player {
    int id;
    String nombre;
    double saldo;
    char posicion;
    boolean esMiTurno;
    boolean enJuego;


    //Métodos
    void perder() {
        if(this.saldo <= 0){
            enJuego = false;
            System.out.println(this.nombre + " está fuera del juego.");
        }
        else{
            enJuego = true;
            System.out.println(this.nombre + " sigue en el juego.");
        }
    }


    Player(int id, String nombre) { //Constructor
        this.id = id;
        this.nombre = nombre;
        this.saldo = 1500;

    }

}