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

    void lanzarDados(){
        if(this.esMiTurno){
            Dado dado = new Dado();
            int r1,r2;
            r1 = dado.lanzar();
            r2 = dado.lanzar();
            int resultado = r1 + r2;
            System.out.println(this.nombre + " rodó un " + resultado);
        }
        else{
            System.out.println("No es tu turno " + this.nombre);
        }
    }


    Player(int id, String nombre) { //Constructor
        this.id = id;
        this.nombre = nombre;
        this.saldo = 1500;
        this.posicion = 'A';

    }

}