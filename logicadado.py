from machine import Pin
from time import sleep
import random


# ==========================================
# BOTÓN
# ==========================================

boton = Pin(14, Pin.IN, Pin.PULL_UP)


# ==========================================
# DISPLAY 1
# A, B, C, D, E, F, G
# ==========================================

display1 = [
    Pin(2, Pin.OUT),   # A
    Pin(3, Pin.OUT),   # B
    Pin(4, Pin.OUT),   # C
    Pin(5, Pin.OUT),   # D
    Pin(6, Pin.OUT),   # E
    Pin(7, Pin.OUT),   # F
    Pin(8, Pin.OUT)    # G
]


# ==========================================
# DISPLAY 2
# A, B, C, D, E, F, G
# ==========================================

display2 = [
    Pin(9, Pin.OUT),    # A
    Pin(10, Pin.OUT),   # B
    Pin(11, Pin.OUT),   # C
    Pin(12, Pin.OUT),   # D
    Pin(13, Pin.OUT),   # E
    Pin(22, Pin.OUT),   # F
    Pin(26, Pin.OUT)    # G
]


# ==========================================
# PATRONES DEL 1 AL 6
# ==========================================

numeros = {

    1: [0,1,1,0,0,0,0],

    2: [1,1,0,1,1,0,1],

    3: [1,1,1,1,0,0,1],

    4: [0,1,1,0,0,1,1],

    5: [1,0,1,1,0,1,1],

    6: [1,0,1,1,1,1,1]
}


# ==========================================
# MOSTRAR UN NÚMERO
# ==========================================

def mostrar(display, numero):

    patron = numeros[numero]

    for i in range(7):

        display[i].value(
            patron[i]
        )


# ==========================================
# ANIMACIÓN + RESULTADO
# ==========================================

def tirar_dados():

    print("Tirando dados...")


    # ------------------------------
    # Animación rápida
    # ------------------------------

    for i in range(15):

        numero1 = random.randint(1, 6)
        numero2 = random.randint(1, 6)

        mostrar(display1, numero1)
        mostrar(display2, numero2)

        sleep(0.05)


    # ------------------------------
    # Empieza a frenarse
    # ------------------------------

    tiempos = [
        0.08,
        0.10,
        0.15,
        0.20,
        0.30
    ]


    for tiempo in tiempos:

        numero1 = random.randint(1, 6)
        numero2 = random.randint(1, 6)

        mostrar(display1, numero1)
        mostrar(display2, numero2)

        sleep(tiempo)


    # ------------------------------
    # RESULTADOS DEFINITIVOS
    # ------------------------------

    dado1 = random.randint(1, 6)
    dado2 = random.randint(1, 6)


    mostrar(display1, dado1)
    mostrar(display2, dado2)


    print("----------------")
    print("Dado 1:", dado1)
    print("Dado 2:", dado2)
    print("Total:", dado1 + dado2)
    print("----------------")

    # Línea que lee Java: prefijo fijo + valores separados por coma
    print("DADOS,{},{}".format(dado1, dado2))

# ==========================================
# PROGRAMA PRINCIPAL
# ==========================================

print("DADOS LISTOS")
print("Presiona el boton")


while True:

    # Botón presionado
    if boton.value() == 0:

        tirar_dados()


        # Esperar hasta que sueltes
        # físicamente el botón
        while boton.value() == 0:

            sleep(0.01)


        # Anti-rebote
        sleep(0.2)